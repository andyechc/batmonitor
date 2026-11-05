package com.andyechc.batmonitor.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BmsProtocolTest {
    /** Basic-info response captured from a real "Andy 72x45" battery (cmd 0x03, status 0x00). */
    private val capturedBasicInfo: List<Int> = listOf(
        0xDD, 0x03, 0x00, 0x26, 0x1F, 0x3A, 0x00, 0x00, 0x0D, 0x4B, 0x10, 0x68, 0x00, 0x25, 0x32, 0xF3,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7D, 0x51, 0x03, 0x18, 0x03, 0x0B, 0xD0, 0x0B, 0xC5, 0x0B,
        0xC6, 0x00, 0x00, 0x00, 0x10, 0x68, 0x0D, 0x4B, 0x00, 0x00, 0xF9, 0x2F, 0x77,
    )

    @Test
    fun readCommandMatchesProtocol() {
        assertEquals(listOf(0xDD, 0xA5, 0x03, 0x00, 0xFF, 0xFD, 0x77), BmsCodec.readCommand(0x03))
        assertEquals(listOf(0xDD, 0xA5, 0x04, 0x00, 0xFF, 0xFC, 0x77), BmsCodec.readCommand(0x04))
    }

    @Test
    fun switchesOnUsesZeroLockByte() {
        assertEquals(
            listOf(0xDD, 0x5A, 0xE1, 0x01, 0x00, 0xFF, 0x1E, 0x77),
            BmsCodec.switches(charge = true, discharge = true),
        )
    }

    @Test
    fun chargeOffSetsChargeLockBit() {
        assertEquals(
            listOf(0xDD, 0x5A, 0xE1, 0x01, 0x01, 0xFF, 0x1D, 0x77),
            BmsCodec.switches(charge = false, discharge = true),
        )
    }

    @Test
    fun balanceCommand() {
        assertEquals(
            listOf(0xDD, 0x5A, 0x0A, 0x02, 0x07, 0x00, 0xFF, 0xED, 0x77),
            BmsCodec.balance,
        )
    }

    @Test
    fun bleNameFrameUsesOneByteChecksum() {
        assertEquals(listOf(0xFF, 0xAA, 0x07, 0x01, 0x41, 0x49), BmsCodec.bleName("A"))
    }

    @Test
    fun bleNameEncodesUtf8() {
        assertEquals(listOf(0xC3, 0xB1), BmsCodec.bleName("ñ").subList(4, 6))
    }

    @Test
    fun nameAcknowledgementStatus() {
        assertEquals(0, BmsCodec.parseNameResponse(listOf(0xFF, 0xAA, 0x07, 0x00, 0x00))?.status)
        assertEquals(1, BmsCodec.parseNameResponse(listOf(0xFF, 0xAA, 0x07, 0x01, 0x00))?.status)
        assertNull(BmsCodec.parseNameResponse(listOf(0xDD, 0x03, 0x00)))
    }

    @Test
    fun decodesCapturedBasicInfoResponse() {
        val frame = BmsCodec.decodeFrame(capturedBasicInfo)!!
        assertEquals(0x03, frame.cmd)
        assertEquals(0, frame.status)
        assertEquals(0x26, frame.data.size)
    }

    @Test
    fun parsesCapturedBasicInfo() {
        val frame = BmsCodec.decodeFrame(capturedBasicInfo)!!
        val info = BmsCodec.parseBasicInfo(frame.data)!!
        assertEquals(79.94, info.totalVoltage, 0.001)
        assertEquals(0.0, info.current, 0.001)
        assertEquals(81, info.soc)
        assertEquals(37, info.cycles)
        assertEquals(24, info.cellCount)
        assertEquals("2025-07-19", info.productionDate)
        assertEquals("7.13", info.firmwareVersion)
        assertTrue(info.chargeOn)
        assertTrue(info.dischargeOn)
        assertEquals(3, info.temperatures.size)
    }

    @Test
    fun rejectsBadChecksum() {
        val frame = capturedBasicInfo.toMutableList()
        frame[frame.size - 2] = frame[frame.size - 2] xor 0xFF
        assertNull(BmsCodec.decodeFrame(frame))
    }

    @Test
    fun parserReassemblesSplitFramesAfterGarbage() {
        val parser = BmsFrameParser()
        val head = capturedBasicInfo.subList(0, 20)
        val tail = capturedBasicInfo.subList(20, capturedBasicInfo.size)
        assertTrue(parser.push(listOf(0x00, 0x11) + head).isEmpty())
        val frames = parser.push(tail)
        assertEquals(1, frames.size)
        assertEquals(0x03, frames.first().cmd)
    }

    @Test
    fun parsesCellVoltagesInVolts() {
        assertEquals(listOf(3.332, 3.331), BmsCodec.parseCellVoltages(listOf(0x0D, 0x04, 0x0D, 0x03)))
    }
}
