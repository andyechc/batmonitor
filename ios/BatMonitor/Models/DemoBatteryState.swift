import SwiftUI

enum DemoBatteryState: String, CaseIterable, Identifiable {
    case charging
    case discharging
    case idle
    case connecting
    case disconnected
    case protection

    var id: Self { self }

    var title: LocalizedStringKey {
        switch self {
        case .charging: "demo.state.charging"
        case .discharging: "demo.state.discharging"
        case .idle: "demo.state.idle"
        case .connecting: "demo.state.connecting"
        case .disconnected: "demo.state.disconnected"
        case .protection: "demo.state.protection"
        }
    }

    var icon: String {
        switch self {
        case .charging: "bolt.fill"
        case .discharging: "arrow.down"
        case .idle: "pause.fill"
        case .connecting: "arrow.triangle.2.circlepath"
        case .disconnected: "wifi.slash"
        case .protection: "exclamationmark.shield.fill"
        }
    }

    var status: BatteryMonitor.Status {
        switch self {
        case .connecting: .connecting
        case .disconnected: .error
        default: .live
        }
    }

    var message: String? {
        self == .disconnected ? String(localized: "demo.disconnectedMessage") : nil
    }

    var reading: BatteryReading? {
        guard self != .connecting else { return nil }

        let soc: Int
        let voltage: Double
        let current: Double
        let remaining: Double
        let protectionActive: Bool

        switch self {
        case .charging:
            (soc, voltage, current, remaining, protectionActive) = (64, 81.42, 8.4, 26.88, false)
        case .discharging:
            (soc, voltage, current, remaining, protectionActive) = (57, 79.68, -3.2, 23.94, false)
        case .idle, .connecting:
            (soc, voltage, current, remaining, protectionActive) = (100, 80.36, 0, 41.98, false)
        case .disconnected:
            (soc, voltage, current, remaining, protectionActive) = (72, 80.12, 0, 30.24, false)
        case .protection:
            (soc, voltage, current, remaining, protectionActive) = (91, 82.10, -1.8, 38.22, true)
        }

        let protections = (0..<13).map { index in
            BMSProtection(labelKey: "protection.\(index)", active: protectionActive && index == 0)
        }
        let baseCell = voltage / 24
        let cells = (0..<24).map { index in
            baseCell + Double((index % 5) - 2) * (protectionActive ? 0.012 : 0.002)
        }
        let basic = BMSBasicInfo(
            totalVoltage: voltage,
            current: current,
            remainingCapacity: remaining,
            nominalCapacity: 42,
            cycles: 37,
            productionDate: "2025-07-19",
            soc: soc,
            firmwareVersion: "7.13",
            chargeOn: true,
            dischargeOn: true,
            cellCount: 24,
            balancing: Array(repeating: false, count: 24),
            protections: protections,
            temperatures: protectionActive ? [45.2, 39.4, 38.9] : [27.4, 27.8, 28.1]
        )
        return BatteryReading(basic: basic, cells: cells, receivedAt: Date())
    }
}
