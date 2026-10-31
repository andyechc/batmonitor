import Foundation
import Observation

/// Keeps one battery connected and polling. Run it from a SwiftUI `.task`, which cancels it when the screen closes.
@MainActor
@Observable
final class BatteryMonitor {
    enum Status: Equatable {
        case connecting
        case live
        case error
    }

    let id: UUID
    private(set) var status: Status = .connecting
    private(set) var reading: BatteryReading?
    private(set) var message: String?

    init(id: UUID) {
        self.id = id
    }

    func run() async {
        var connected = false
        while !Task.isCancelled {
            do {
                if !connected {
                    status = .connecting
                    try await BLECentral.shared.connect(id: id)
                    connected = true
                }
                reading = try await BLECentral.shared.readBattery(id: id)
                status = .live
                message = nil
                try await Task.sleep(nanoseconds: 2_000_000_000)
            } catch {
                connected = false
                BLECentral.shared.disconnect(id)
                if Task.isCancelled { break }
                message = error.localizedDescription
                status = .error
                try? await Task.sleep(nanoseconds: 4_000_000_000)
            }
        }
        BLECentral.shared.disconnect(id)
    }

    func setSwitches(charge: Bool, discharge: Bool) async throws {
        try await send(BMSCodec.switches(charge: charge, discharge: discharge), cmd: BMSCommand.switches)
    }

    func balance() async throws {
        try await send(BMSCodec.balance, cmd: BMSCommand.control)
    }

    func clearProtection() async throws {
        try await send(BMSCodec.clearProtection, cmd: BMSCommand.control)
    }

    /// Writes the name into the BMS. The caller saves it locally only after this succeeds.
    func rename(to name: String) async throws {
        guard status == .live else { throw BLEError.notConnected }
        let ack = try await BLECentral.shared.command(
            id: id,
            payload: BMSCodec.bleName(name),
            cmd: BMSCommand.bleName
        )
        guard ack.status == 0 else { throw BLEError.writeFailed }
    }

    private func send(_ payload: [UInt8], cmd: UInt8) async throws {
        guard status == .live else { throw BLEError.notConnected }
        let frame = try await BLECentral.shared.command(id: id, payload: payload, cmd: cmd)
        guard frame.status == 0 else { throw BLEError.writeFailed }
    }
}
