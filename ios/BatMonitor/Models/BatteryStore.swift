import Foundation
import Observation

struct SavedBattery: Codable, Identifiable, Hashable {
    let id: UUID
    var name: String
    let addedAt: Date
}

@MainActor
@Observable
final class BatteryStore {
    private(set) var batteries: [SavedBattery]
    private let defaults: UserDefaults
    private static let key = "batmonitor.batteries.v2"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: Self.key),
           let decoded = try? JSONDecoder().decode([SavedBattery].self, from: data) {
            batteries = decoded
        } else {
            batteries = []
        }
    }

    func battery(id: UUID) -> SavedBattery? {
        batteries.first { $0.id == id }
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
        save()
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
}
