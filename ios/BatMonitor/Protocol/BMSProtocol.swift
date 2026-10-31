import Foundation

enum BMSCommand {
    static let basicInfo: UInt8 = 0x03
    static let cellVoltages: UInt8 = 0x04
    static let control: UInt8 = 0x0A
    static let switches: UInt8 = 0xE1
    static let bleName: UInt8 = 0x07
}

struct BMSFrame: Equatable {
    let status: UInt8
    let cmd: UInt8
    let data: [UInt8]
}

struct BMSProtection: Equatable {
    let labelKey: String
    let active: Bool
}

struct BMSBasicInfo: Equatable {
    var totalVoltage: Double
    /// Amperes. Positive while charging, negative while discharging.
    var current: Double
    /// Remaining capacity in Ah.
    var remainingCapacity: Double
    /// Nominal capacity in Ah.
    var nominalCapacity: Double
    var cycles: Int
    var productionDate: String
    var soc: Int
    var firmwareVersion: String
    var chargeOn: Bool
    var dischargeOn: Bool
    var cellCount: Int
    var balancing: [Bool]
    var protections: [BMSProtection]
    var temperatures: [Double]
}

enum BMSChecksum {
    /// Request checksum: ~(cmd + len + sum(data)) + 1, low 16 bits.
    static func request(cmd: UInt8, data: [UInt8]) -> UInt16 {
        var sum = Int(cmd) + data.count
        for byte in data { sum += Int(byte) }
        return UInt16(truncatingIfNeeded: (~sum + 1) & 0xFFFF)
    }

    /// Response checksum covers only length and data, not cmd or status.
    static func response(data: [UInt8]) -> UInt16 {
        var sum = data.count
        for byte in data { sum += Int(byte) }
        return UInt16(truncatingIfNeeded: (~sum + 1) & 0xFFFF)
    }
}

enum BMSCodec {
    static func readCommand(_ cmd: UInt8) -> [UInt8] {
        let sum = BMSChecksum.request(cmd: cmd, data: [])
        return [0xDD, 0xA5, cmd, 0x00, UInt8(sum >> 8), UInt8(sum & 0xFF), 0x77]
    }

    static func writeCommand(_ cmd: UInt8, _ content: [UInt8]) -> [UInt8] {
        let sum = BMSChecksum.request(cmd: cmd, data: content)
        return [0xDD, 0x5A, cmd, UInt8(content.count)] + content + [UInt8(sum >> 8), UInt8(sum & 0xFF), 0x77]
    }

    /// Charge and discharge MOSFET switches. A bit set to 1 turns that path off.
    static func switches(charge: Bool, discharge: Bool) -> [UInt8] {
        let lock: UInt8 = (charge ? 0 : 1) | ((discharge ? 0 : 1) << 1)
        return writeCommand(BMSCommand.switches, [lock])
    }

    static let balance = writeCommand(BMSCommand.control, [0x07, 0x00])
    static let clearProtection = writeCommand(BMSCommand.control, [0x04, 0x00])

    /// Bluetooth name stored in the BMS. Uses its own frame format, FF AA 07.
    static func bleName(_ name: String) -> [UInt8] {
        let bytes = Array(name.utf8.prefix(255))
        let sum = bytes.reduce(0) { $0 + Int($1) } + ((bytes.count + Int(BMSCommand.bleName)) & 0xFF)
        return [0xFF, 0xAA, BMSCommand.bleName, UInt8(bytes.count)] + bytes + [UInt8(sum & 0xFF)]
    }

    /// Name-write acknowledgement: FF AA 07 <status>. Status 0 means stored.
    static func parseNameResponse(_ bytes: [UInt8]) -> BMSFrame? {
        guard bytes.count >= 4, bytes[0] == 0xFF, bytes[1] == 0xAA, bytes[2] == BMSCommand.bleName else {
            return nil
        }
        return BMSFrame(status: bytes[3], cmd: BMSCommand.bleName, data: Array(bytes.dropFirst(4)))
    }

    static func decodeFrame(_ bytes: [UInt8]) -> BMSFrame? {
        guard bytes.count >= 7, bytes.first == 0xDD, bytes.last == 0x77 else { return nil }
        let cmd = bytes[1]
        let status = bytes[2]
        let length = Int(bytes[3])
        guard bytes.count == length + 7 else { return nil }
        let data = Array(bytes[4..<(4 + length)])
        let received = (UInt16(bytes[4 + length]) << 8) | UInt16(bytes[5 + length])
        guard BMSChecksum.response(data: data) == received else { return nil }
        return BMSFrame(status: status, cmd: cmd, data: data)
    }

    static func parseBasicInfo(_ d: [UInt8]) -> BMSBasicInfo? {
        guard d.count >= 23 else { return nil }
        let ntcCount = Int(d[22])
        guard d.count >= 23 + ntcCount * 2 else { return nil }

        func u16(_ i: Int) -> Int { Int(d[i]) << 8 | Int(d[i + 1]) }
        func s16(_ i: Int) -> Int { Int(Int16(bitPattern: UInt16(u16(i)))) }

        let cellCount = Int(d[21])
        let balanceBytes = [13, 12, 15, 14]
        let balancing = (0..<min(cellCount, 32)).map { i in
            (Int(d[balanceBytes[i / 8]]) >> (i % 8)) & 1 == 1
        }

        let protections = (0..<13).map { i in
            let byte = i < 8 ? d[17] : d[16]
            return BMSProtection(labelKey: "protection.\(i)", active: (Int(byte) >> (i % 8)) & 1 == 1)
        }

        let temperatures = (0..<ntcCount).map { i in Double(u16(23 + i * 2) - 2731) / 10 }

        let dateCode = u16(10)
        let year = (dateCode >> 9) + 2000
        let month = (dateCode >> 5) & 0x0F
        let day = dateCode & 0x1F

        return BMSBasicInfo(
            totalVoltage: Double(u16(0)) / 100,
            current: Double(s16(2)) / 100,
            remainingCapacity: Double(u16(4)) / 100,
            nominalCapacity: Double(u16(6)) / 100,
            cycles: u16(8),
            productionDate: String(format: "%04ld-%02ld-%02ld", year, month, day),
            soc: Int(d[19]),
            firmwareVersion: "\(d[18] >> 4).\(d[18] & 0x0F)",
            chargeOn: d[20] & 0x01 == 0x01,
            dischargeOn: d[20] & 0x02 == 0x02,
            cellCount: cellCount,
            balancing: balancing,
            protections: protections,
            temperatures: temperatures
        )
    }

    /// Cell voltages in volts, one entry per cell.
    static func parseCellVoltages(_ d: [UInt8]) -> [Double] {
        stride(from: 0, to: d.count - 1, by: 2).map { i in
            Double(Int(d[i]) << 8 | Int(d[i + 1])) / 1000
        }
    }
}

/// Reassembles BLE notification chunks into complete BMS response frames.
final class BMSFrameParser {
    private var buffer: [UInt8] = []

    func reset() {
        buffer = []
    }

    func push(_ chunk: [UInt8]) -> [BMSFrame] {
        buffer += chunk
        var frames: [BMSFrame] = []
        while buffer.count >= 7 {
            guard let start = buffer.firstIndex(of: 0xDD) else {
                buffer = []
                break
            }
            if start > 0 { buffer.removeFirst(start) }
            if buffer.count < 4 { break }
            let total = Int(buffer[3]) + 7
            if buffer.count < total { break }
            if let frame = BMSCodec.decodeFrame(Array(buffer[0..<total])) {
                frames.append(frame)
                buffer.removeFirst(total)
            } else {
                buffer.removeFirst(1)
            }
        }
        return frames
    }
}
