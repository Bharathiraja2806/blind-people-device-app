"""Small, dependency-light helpers shared by export validation and Python tests."""
from __future__ import annotations
import numpy as np

def letterbox_rgb(image: np.ndarray, size: int = 640):
    if image.ndim != 3 or image.shape[2] != 3 or image.size == 0:
        raise ValueError("Expected a non-empty HxWx3 RGB image")
    h, w = image.shape[:2]
    scale = min(size / w, size / h)
    rw, rh = round(w * scale), round(h * scale)
    # Pillow keeps this module dependency-light; caller supplies uint8 RGB.
    from PIL import Image
    resized = np.asarray(Image.fromarray(image.astype(np.uint8)).resize((rw, rh), Image.Resampling.BILINEAR))
    canvas = np.full((size, size, 3), 114, dtype=np.uint8)
    left, top = (size-rw)//2, (size-rh)//2
    canvas[top:top+rh, left:left+rw] = resized
    return canvas.transpose(2, 0, 1).astype(np.float32) / 255.0, (scale, left, top, w, h)

def decode_yolo(output: np.ndarray, labels: list[str], threshold: float = .35):
    a = np.asarray(output)
    if a.ndim == 3 and a.shape[0] == 1: a = a[0]
    if a.ndim != 2: raise ValueError(f"Expected one rank-2 prediction tensor, got {a.shape}")
    attributes=len(labels)+4
    if (a.shape[0] == attributes and a.shape[1] != attributes) or (a.shape[0] < attributes and a.shape[1] > a.shape[0]): a = a.T
    if a.shape[1] < len(labels)+4: raise ValueError("Prediction tensor does not contain xywh and all class scores")
    scores=a[:,4:4+len(labels)]; classes=scores.argmax(axis=1); confidence=scores.max(axis=1)
    result=[]
    for row, cls, conf in zip(a, classes, confidence):
        if not np.isfinite(conf) or conf < threshold: continue
        cx,cy,w,h=map(float,row[:4])
        if not np.isfinite([cx,cy,w,h]).all() or w <= 0 or h <= 0: continue
        result.append((int(cls), labels[int(cls)], float(conf), (cx-w/2,cy-h/2,cx+w/2,cy+h/2)))
    return result

def scale_box(box, meta):
    scale,left,top,width,height=meta
    x1,y1,x2,y2=box
    return tuple(max(0.,min(1.,v)) for v in ((x1-left)/scale/width,(y1-top)/scale/height,(x2-left)/scale/width,(y2-top)/scale/height))
