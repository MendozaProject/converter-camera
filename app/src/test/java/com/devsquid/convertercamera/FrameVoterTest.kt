package com.devsquid.convertercamera

import com.devsquid.convertercamera.business.FrameVoter
import com.devsquid.convertercamera.business.FrameVoter.Box
import com.devsquid.convertercamera.business.FrameVoter.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FrameVoterTest {

    private val voter = FrameVoter(minVotes = 2)

    private fun det(value: String, left: Float, top: Float, label: String = value) =
        Detection(Box(left, top, left + 100f, top + 30f), BigDecimal(value), label)

    @Test
    fun sporadicMisreadIsOutvoted() {
        val result = voter.vote(
            listOf(
                listOf(det("474", 100f, 200f)),
                listOf(det("74", 104f, 198f)),
                listOf(det("74", 98f, 203f)),
            )
        )
        assertEquals(1, result.size)
        assertEquals("74", result[0].payload)
        assertEquals(2, result[0].votes)
    }

    @Test
    fun returnedBoxIsInReferenceFrameCoordinates() {
        val result = voter.vote(
            listOf(
                listOf(det("474", 100f, 200f)),
                listOf(det("74", 130f, 215f)),
                listOf(det("74", 130f, 215f)),
            )
        )
        assertEquals(100f, result.single().box.left)
        assertEquals(200f, result.single().box.top)
    }

    @Test
    fun handShakeIsCompensatedAcrossManyPrices() {
        // Frames 1 and 2 are shifted by more than a box height; alignment still pairs them up.
        val ref = listOf(det("100", 0f, 0f), det("200", 0f, 50f), det("300", 0f, 100f))
        val shifted = ref.map { it.copy(box = it.box.offset(20f, 40f)) }
        val result = voter.vote(listOf(ref, shifted, shifted))
        assertEquals(3, result.size)
        assertTrue(result.all { it.votes == 3 })
    }

    @Test
    fun pricesOnSameLineBoxAreKeptApartByValue() {
        // ML Kit reports the line box for every element, so two prices can share one box.
        val box = Box(0f, 0f, 300f, 30f)
        fun frame(vararg values: String) = values.map { Detection(box, BigDecimal(it), it) }
        val result = voter.vote(listOf(frame("500", "120"), frame("120", "500"), frame("500", "120")))
        assertEquals(setOf("500", "120"), result.map { it.payload }.toSet())
        assertTrue(result.all { it.votes == 3 })
    }

    @Test
    fun referenceDetectionIsKeptEvenWhenOtherFramesMissIt() {
        val result = voter.vote(listOf(listOf(det("74", 0f, 0f)), emptyList(), emptyList()))
        assertEquals("74", result.single().payload)
    }

    @Test
    fun detectionMissingFromReferenceNeedsMajority() {
        val once = voter.vote(listOf(emptyList(), listOf(det("90", 0f, 0f)), emptyList()))
        assertTrue(once.isEmpty())

        val twice = voter.vote(listOf(emptyList(), listOf(det("90", 0f, 0f)), listOf(det("90", 2f, 1f))))
        assertEquals("90", twice.single().payload)
    }

    @Test
    fun tieGoesToEarliestFrame() {
        val result = voter.vote(listOf(listOf(det("74", 0f, 0f)), listOf(det("474", 0f, 0f))))
        assertEquals("74", result.single().payload)
    }

    @Test
    fun equalValuesWithDifferentScaleVoteTogether() {
        val result = voter.vote(
            listOf(listOf(det("74.0", 0f, 0f, "a")), listOf(det("74", 0f, 0f, "b")), listOf(det("474", 0f, 0f)))
        )
        assertEquals("a", result.single().payload)
        assertEquals(2, result.single().votes)
    }
}
