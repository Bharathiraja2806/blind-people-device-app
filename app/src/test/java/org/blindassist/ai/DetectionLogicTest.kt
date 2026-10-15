package org.blindassist.ai

import org.junit.Assert.*
import org.junit.Test

class DetectionLogicTest {
    @Test fun positionsUseNormalizedImageCoordinates() {
        assertEquals(RelativePosition.LEFT,DetectionLogic.position(Detection(0,"person",.8f,.1f,.1f,.3f,.5f)))
        assertEquals(RelativePosition.CENTER,DetectionLogic.position(Detection(0,"person",.8f,.4f,.1f,.6f,.5f)))
        assertEquals(RelativePosition.RIGHT,DetectionLogic.position(Detection(0,"person",.8f,.7f,.1f,.9f,.5f)))
    }
    @Test fun alertIsAbsentForNoDetections() { assertNull(DetectionLogic.alert(emptyList())) }
    @Test fun alertIsConservativeAndDirectional() {
        assertEquals("Person ahead.",DetectionLogic.alert(listOf(Detection(0,"person",.9f,.4f,.1f,.6f,.6f))))
        assertEquals("Chair on your left.",DetectionLogic.alert(listOf(Detection(1,"chair",.9f,.1f,.1f,.3f,.6f))))
    }
    @Test fun nmsSuppressesOverlappingSameClass() {
        val a=Detection(1,"chair",.9f,.1f,.1f,.6f,.6f); val b=a.copy(confidence=.7f,left=.12f,top=.12f,right=.58f,bottom=.58f)
        assertEquals(listOf(a),DetectionLogic.nms(listOf(a,b)))
    }
    @Test fun alertGateAppliesCooldownAndAllowsChangedMessage() {
        val gate=AlertGate(1000)
        assertTrue(gate.shouldSpeak("Person ahead.",100)); assertFalse(gate.shouldSpeak("Person ahead.",200))
        assertFalse(gate.shouldSpeak("Car ahead.",250)); assertTrue(gate.shouldSpeak("Car ahead.",1100))
    }
}
