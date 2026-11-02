import Foundation
import Observation

struct SavedBattery: Codable, Identifiable, Hashable {
    let id: UUID
    var name: String
    let addedAt: Date
    var isDemo: Bool? = nil

    static let demo = SavedBattery(
        id: UUID(uuidString: "A73B1487-8B90-4A74-9512-273B9F100001")!,
        name: "BatMonitor · TEST",
        addedAt: .distantPast,
        isDemo: true
    )
}

struct BatterySnapshot: Codable, Equatable {
    let soc: Int
    let voltage: Double
    let current: Double
    let receivedAt: Date
}

@MainActor
@Observable
final class BatteryStore {
    private(set) var batteries: [SavedBattery]
    private(set) var snapshots: [UUID: BatterySnapshot]
    private let defaults: UserDefaults
    private static let key = "batmonitor.batteries.v2"
    private static let snapshotKey = "batmonitor.snapshots.v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: Self.key),
           let decoded = try? JSONDecoder().decode([SavedBattery].self, from: data) {
            batteries = decoded
        } else {
            batteries = []
        }
        if let data = defaults.data(forKey: Self.snapshotKey),
           let decoded = try? JSONDecoder().decode([UUID: BatterySnapshot].self, from: data) {
            snapshots = decoded
        } else {
            snapshots = [:]
        }
        // UI-test hook (in-memory only, never saved): seeds a fake real
        // battery so the non-demo menu can be screenshotted in the
        // simulator without BLE hardware.
        if CommandLine.arguments.contains("UITEST_FAKE_BATTERY") {
            let id = UUID(uuidString: "A73B1487-8B90-4A74-9512-273B9F100002")!
            if battery(id: id) == nil {
                batteries.append(SavedBattery(id: id, name: "UITEST Battery", addedAt: Date()))
            }
        }
    }

    func battery(id: UUID) -> SavedBattery? {
        batteries.first { $0.id == id }
    }

    func snapshot(id: UUID) -> BatterySnapshot? {
        snapshots[id]
    }

    func record(_ reading: BatteryReading, for id: UUID) {
        guard battery(id: id) != nil else { return }
        snapshots[id] = BatterySnapshot(
            soc: reading.basic.soc,
            voltage: reading.basic.totalVoltage,
            current: reading.basic.current,
            receivedAt: reading.receivedAt
        )
        saveSnapshots()
    }

    func add(id: UUID, name: String) {
        guard battery(id: id) == nil else { return }
        batteries.append(SavedBattery(id: id, name: name, addedAt: Date()))
        save()
    }

    func rename(id: UUID, to name: String) {
        guard let index = batteries.firstIndex(where: { $0.id == id }) else { return }
        batteries[index].name = name
        save()
    }

    func remove(id: UUID) {
        batteries.removeAll { $0.id == id }
        snapshots[id] = nil
        save()
        saveSnapshots()
    }

    func remove(at offsets: IndexSet) {
        batteries.remove(atOffsets: offsets)
        save()
    }

    private func save() {
        if let data = try? JSONEncoder().encode(batteries) {
            defaults.set(data, forKey: Self.key)
        }
    }

    private func saveSnapshots() {
        if let data = try? JSONEncoder().encode(snapshots) {
            defaults.set(data, forKey: Self.snapshotKey)
        }
    }
}
