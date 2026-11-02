import XCTest
@testable import BatMonitor

final class DemoBatteryStateTests: XCTestCase {
    func testConnectingStateShowsLoadingWithoutFakeReading() {
        XCTAssertEqual(DemoBatteryState.connecting.status, .connecting)
        XCTAssertNil(DemoBatteryState.connecting.reading)
    }

    func testDisconnectedStateKeepsAReadingAndExplainsFailure() {
        XCTAssertEqual(DemoBatteryState.disconnected.status, .error)
        XCTAssertNotNil(DemoBatteryState.disconnected.reading)
        XCTAssertNotNil(DemoBatteryState.disconnected.message)
    }

    func testChargingAndDischargingHaveOppositeCurrentSigns() throws {
        let charging = try XCTUnwrap(DemoBatteryState.charging.reading)
        let discharging = try XCTUnwrap(DemoBatteryState.discharging.reading)
        XCTAssertGreaterThan(charging.basic.current, 0)
        XCTAssertLessThan(discharging.basic.current, 0)
    }

    func testProtectionScenarioSetsAnActiveProtection() throws {
        let reading = try XCTUnwrap(DemoBatteryState.protection.reading)
        XCTAssertTrue(reading.basic.protections.contains(where: \.active))
    }

    func testExistingSavedBatteryDataDecodesWithoutDemoFlag() throws {
        let json = #"{"id":"A73B1487-8B90-4A74-9512-273B9F100002","name":"Existing battery","addedAt":0}"#.data(using: .utf8)!
        let battery = try JSONDecoder().decode(SavedBattery.self, from: json)
        XCTAssertFalse(battery.isDemo ?? false)
    }
}
