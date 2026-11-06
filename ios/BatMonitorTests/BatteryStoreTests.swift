import XCTest
@testable import BatMonitor

@MainActor
final class BatteryStoreTests: XCTestCase {
    private let batteriesKey = "batmonitor.batteries.v2"
    private let snapshotsKey = "batmonitor.snapshots.v1"

    func testLoadDropsRetiredBatteryAndItsSnapshot() throws {
        let suite = "batmonitor.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }

        let retiredID = UUID(uuidString: "A73B1487-8B90-4A74-9512-273B9F100001")!
        let real = SavedBattery(id: UUID(), name: "Real battery", addedAt: Date())
        let retired = SavedBattery(id: retiredID, name: "Old fixture", addedAt: .distantPast)
        defaults.set(try JSONEncoder().encode([retired, real]), forKey: batteriesKey)
        defaults.set(
            try JSONEncoder().encode([retiredID: BatterySnapshot(soc: 50, voltage: 50, current: 0, receivedAt: Date())]),
            forKey: snapshotsKey
        )

        let store = BatteryStore(defaults: defaults)
        XCTAssertEqual(store.batteries.map(\.id), [real.id])
        XCTAssertNil(store.snapshot(id: retiredID))

        // The cleaned list is persisted, so the ghost never comes back.
        let data = try XCTUnwrap(defaults.data(forKey: batteriesKey))
        let saved = try JSONDecoder().decode([SavedBattery].self, from: data)
        XCTAssertEqual(saved.map(\.id), [real.id])
    }

    func testExistingSavedBatteryPayloadStillDecodes() throws {
        // Older builds persisted an extra boolean flag; unknown keys must be
        // ignored so an existing install keeps its batteries.
        let json = #"[{"id":"A73B1487-8B90-4A74-9512-273B9F100002","name":"Existing battery","addedAt":0,"legacyFlag":false}]"#
            .data(using: .utf8)!
        let decoded = try JSONDecoder().decode([SavedBattery].self, from: json)
        XCTAssertEqual(decoded.count, 1)
        XCTAssertEqual(decoded.first?.name, "Existing battery")
    }
}
