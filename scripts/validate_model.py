import json, sys
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1]; model=ROOT/"app/src/main/assets/yolo.onnx"; manifest=ROOT/"app/src/main/assets/model_manifest.json"
if not model.is_file() or model.stat().st_size < 100_000: raise SystemExit(f"FAIL: ONNX model missing or too small: {model}. Run python scripts/export_model.py")
try:
    import onnx, onnxruntime as ort
    onnx.checker.check_model(onnx.load(str(model)))
    info=json.loads(manifest.read_text(encoding="utf-8")); session=ort.InferenceSession(str(model),providers=["CPUExecutionProvider"])
    inp=session.get_inputs()[0]
    if inp.shape != [1,3,640,640] or inp.type != "tensor(float)": raise ValueError(f"Unexpected input {inp.shape} {inp.type}")
    outputs=session.run(None,{inp.name:np.zeros((1,3,640,640),dtype=np.float32)})
    if not outputs or not np.isfinite(outputs[0]).all(): raise ValueError("Invalid inference output")
    print(f"PASS: {info['model']}, input={inp.shape}, outputs={[x.shape for x in outputs]}, bytes={model.stat().st_size}")
except Exception as exc: raise SystemExit(f"FAIL: model validation: {exc}")
