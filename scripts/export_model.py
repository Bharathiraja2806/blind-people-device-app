from __future__ import annotations
import json, os, shutil
from pathlib import Path
import numpy as np
from vision import letterbox_rgb, decode_yolo

ROOT=Path(__file__).resolve().parents[1]
os.environ.setdefault("YOLO_CONFIG_DIR",str(ROOT/".ultralytics"))
MODEL=ROOT/"app/src/main/assets/yolo.onnx"
MANIFEST=ROOT/"app/src/main/assets/model_manifest.json"
SAMPLE=ROOT/"scripts/sample_bus.jpg"
SIZE=640

def main():
    try:
        import onnx, onnxruntime as ort
        import torch
        from ultralytics import YOLO
    except ImportError as e:
        raise SystemExit(f"Missing export dependency: {e}. Run scripts/setup.ps1 first.")
    name="yolo11n.pt"
    # PyTorch 2.9+ defaults to the dynamo exporter, whose operator registry
    # evolves independently of the pinned legacy Ultralytics exporter. Use
    # PyTorch's stable legacy path for this static, fixed-size export.
    original_export = torch.onnx.export
    def legacy_export(*args, **kwargs):
        kwargs.setdefault("dynamo", False)
        return original_export(*args, **kwargs)
    torch.onnx.export = legacy_export
    model=YOLO(name)  # Official Ultralytics pretrained COCO model source/cache.
    exported=Path(model.export(format="onnx", imgsz=SIZE, opset=17, simplify=False, nms=False, dynamic=False))
    if not exported.is_file() or exported.stat().st_size < 100_000: raise RuntimeError("Export did not produce a non-empty ONNX model")
    graph=onnx.load(str(exported)); onnx.checker.check_model(graph)
    sess=ort.InferenceSession(str(exported),providers=["CPUExecutionProvider"])
    ins=sess.get_inputs(); outs=sess.get_outputs()
    if len(ins)!=1 or ins[0].type!="tensor(float)": raise RuntimeError(f"Unexpected input contract: {[(x.name,x.shape,x.type) for x in ins]}")
    shape=ins[0].shape
    if shape != [1,3,SIZE,SIZE]: raise RuntimeError(f"Expected [1,3,{SIZE},{SIZE}], actual {shape}")
    labels=list(model.names.values())
    if not SAMPLE.is_file(): raise RuntimeError(f"Reference image missing: {SAMPLE}")
    sample_result=model.predict(str(SAMPLE),imgsz=SIZE,verbose=False)[0]
    if len(sample_result.boxes)==0: raise RuntimeError("Reference image produced no detections in the pretrained PyTorch model")
    sample_np=np.ascontiguousarray(sample_result.orig_img[:,:,::-1])
    tensor,_=letterbox_rgb(sample_np,SIZE)
    pred=sess.run(None,{ins[0].name:tensor[None]})
    if not pred or np.asarray(pred[0]).size==0: raise RuntimeError("Inference returned an empty output tensor")
    decoded=decode_yolo(pred[0],labels,threshold=.25)
    if not decoded: raise RuntimeError("ONNX reference-image inference produced no detections")
    MODEL.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(exported,MODEL)
    manifest={"model":"Ultralytics YOLO11n","source":"Ultralytics official pretrained yolo11n.pt COCO weights","input":{"name":ins[0].name,"shape":shape,"dtype":ins[0].type,"color":"RGB","layout":"NCHW","size":SIZE},"outputs":[{"name":x.name,"shape":x.shape,"dtype":x.type} for x in outs],"nms_in_graph":False,"export":{"opset":17,"imgsz":SIZE,"dynamic":False,"simplify":False},"onnx_bytes":MODEL.stat().st_size,"sample_inference":{"source":"Ultralytics bus.jpg reference image","detections":len(decoded),"top_classes":[d[1] for d in decoded[:5]],"status":"passed"}}
    MANIFEST.write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(manifest,indent=2))

if __name__=="__main__": main()
