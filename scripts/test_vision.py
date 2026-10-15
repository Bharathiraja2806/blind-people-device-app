import unittest
import numpy as np
from vision import letterbox_rgb, decode_yolo, scale_box

class VisionTests(unittest.TestCase):
    def test_letterbox_and_shape(self):
        tensor,meta=letterbox_rgb(np.zeros((100,200,3),dtype=np.uint8),64)
        self.assertEqual(tensor.shape,(3,64,64)); self.assertEqual(tensor.dtype,np.float32); self.assertEqual(meta[1],0); self.assertGreater(meta[2],0)
    def test_decode_and_threshold(self):
        out=np.zeros((1,6,2),dtype=np.float32); out[0,:,0]=[32,32,20,20,.8,.1]; out[0,:,1]=[10,10,5,5,.2,.3]
        got=decode_yolo(out,["person","chair"],.35)
        self.assertEqual(len(got),1); self.assertEqual(got[0][1],"person")
    def test_invalid_output_and_empty(self):
        self.assertEqual(decode_yolo(np.zeros((1,6,2)),["person","chair"],.5),[])
        with self.assertRaises(ValueError): decode_yolo(np.zeros((3,)),["person"])
    def test_box_mapping(self):
        self.assertEqual(scale_box((10,20,30,40),(2,10,20,100,100)),(0.,0.,.1,.1))

if __name__=="__main__": unittest.main()
