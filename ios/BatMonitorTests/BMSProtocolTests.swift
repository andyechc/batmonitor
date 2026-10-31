import XCTest
@testable import BatMonitor

final class BMSProtocolTests: XCTestCase {
    /// Basic-info response captured from a real "Andy 72x45" battery (cmd 0x03, status 0x00).
    private let capturedBasicInfo: [UInt8] = [
        0xDD, 0x03, 0x00, 0x26, 0x1F, 0x3A, 0x00, 0x00, 0x0D, 0x4B, 0x10, 0x68, 0x00, 0x25, 0x32, 0xF3,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7D, 0x51, 0x03, 0x18, 0x03, 0x0B, 0xD0, 0x0B, 0xC5, 0x0B,
        0xC6, 0x00, 0x00, 0x00, 0x10, 0x68, 0x0D, 0x4B, 0x00, 0x00, 0xF9, 0x2F, 0x77,
    ]

    func testReadCommandMatchesProtocol() {
        XCTAssertEqual(BMSCodec.readCommand(0x03), [0xDD, 0xA5, 0x03, 0x00, 0xFF, 0xFD, 0x77])
        XCTAssertEqual(BMSCodec.readCommand(0x04), [0xDD, 0xA5, 0x04, 0x00, 0xFF, 0xFC, 0x77])
    }

    func testSwitchesOnUsesZeroLockByte() {
        XCTAssertEqual(BMSCodec.switches(charge: true, discharge: true), [0xDD, 0x5A, 0xE1, 0x01, 0x00, 0xFF, 0x1E, 0x77])
    }

    func testChargeOffSetsChargeLockBit() {
        XCTAssertEqual(BMSCodec.switches(charge: false, discharge: true), [0xDD, 0x5A, 0xE1, 0x01, 0x01, 0xFF, 0x1D, 0x77])
    }

    func testBalanceCommand() {
        XCTAssertEqual(BMSCodec.balance, [0xDD, 0x5A, 0x0A, 0x02, 0x07, 0x00, 0xFF, 0xED, 0x77])
    }

    func testBleNameFrameUsesOneByteChecksum() {
        XCTAssertEqual(BMSCodec.bleName("A"), [0xFF, 0xAA, 0x07, 0x01, 0x41, 0x49])
    }

    func testBleNameEncodesUTF8() {
        XCTAssertEqual(Array(BMSCodec.bleName("ñ")[4..<6]), [0xC3, 0xB1])
    }

    func testNameAcknowledgementStatus() {
        XCTAssertEqual(BMSCodec.parseNameResponse([0xFF, 0xAA, 0x07, 0x00, 0x00])?.status, 0)
        XCTAssertEqual(BMSCodec.parseNameResponse([0xFF, 0xAA, 0x07, 0x01, 0x00])?.status, 1)
        XCTAssertNil(BMSCodec.parseNameResponse([0xDD, 0x03, 0x00]))
    }

    func testDecodesCapturedBasicInfoResponse() throws {
        let frame = try XCTUnwrap(BMSCodec.decodeFrame(capturedBasicInfo))
        XCTAssertEqual(frame.cmd, 0x03)
        XCTAssertEqual(frame.status, 0)
        XCTAssertEqual(frame.data.count, 0x26)
    }

    func testParsesCapturedBasicInfo() throws {
        let frame = try XCTUnwrap(BMSCodec.decodeFrame(capturedBasicInfo))
        let info = try XCTUnwrap(BMSCodec.parseBasicInfo(frame.data))
        XCTAssertEqual(info.totalVoltage, 79.94, accuracy: 0.001)
        XCTAssertEqual(info.current, 0, accuracy: 0.001)
        XCTAssertEqual(info.soc, 81)
        XCTAssertEqual(info.cycles, 37)
        XCTAssertEqual(info.cellCount, 24)
        XCTAssertEqual(info.productionDate, "2025-07-19")
        XCTAssertEqual(info.firmwareVersion, "7.13")
        XCTAssertTrue(info.chargeOn)
        XCTAssertTrue(info.dischargeOn)
        XCTAssertEqual(info.temperatures.count, 3)
    }

    func testRejectsBadChecksum() {
        var frame = capturedBasicInfo
        frame[frame.count - 2] ^= 0xFF
        XCTAssertNil(BMSCodec.decodeFrame(frame))
    }

    func testParserReassemblesSplitFramesAfterGarbage() {
        let parser = BMSFrameParser()
        let head = Array(capturedBasicInfo[0..<20])
        let tail = Array(capturedBasicInfo[20...])
        XCTAssertTrue(parser.push([0x00, 0x11] + head).isEmpty)
        let frames = parser.push(tail)
        XCTAssertEqual(frames.count, 1)
        XCTAssertEqual(frames.first?.cmd, 0x03)
    }

    func testParsesCellVoltagesInVolts() {
        XCTAssertEqual(BMSCodec.parseCellVoltages([0x0D, 0x04, 0x0D, 0x03]), [3.332, 3.331])
    }
}
