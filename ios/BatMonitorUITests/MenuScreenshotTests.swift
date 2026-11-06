import XCTest

/// Opens screens and writes screenshots to /tmp so UI can be reviewed
/// without tapping on a physical device. A fake in-memory battery
/// (`UITEST_FAKE_BATTERY`) stands in for real hardware, so nothing here
/// needs Bluetooth.
final class MenuScreenshotTests: XCTestCase {
    private func launchWithFakeBattery() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["UITEST_FAKE_BATTERY"]
        app.launch()
        return app
    }

    func testRealMenuScreenshot() throws {
        let app = launchWithFakeBattery()
        XCTAssertTrue(app.buttons["batteryCard"].waitForExistence(timeout: 15))
        try app.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: "/tmp/shot-home-list.png"))
        app.buttons["batteryCard"].tap()
        XCTAssertTrue(app.buttons["moreMenu"].waitForExistence(timeout: 15))
        app.buttons["moreMenu"].tap()
        sleep(2)
        let shot = app.screenshot()
        try shot.pngRepresentation.write(to: URL(fileURLWithPath: "/tmp/menu-shot-real.png"))
    }

    func testHomeAndAddScreenshots() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["addBattery"].waitForExistence(timeout: 15))
        try app.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: "/tmp/shot-home-empty.png"))
        app.buttons["addBattery"].tap()
        sleep(3)
        try app.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: "/tmp/shot-add.png"))
    }

    func testTabsScreenshots() throws {
        let app = launchWithFakeBattery()
        XCTAssertTrue(app.buttons["batteryCard"].waitForExistence(timeout: 15))
        app.buttons["batteryCard"].tap()
        XCTAssertTrue(app.tabBars.buttons.element(boundBy: 0).waitForExistence(timeout: 15))
        let names = ["overview", "cells", "status", "control"]
        for i in 0 ..< 4 {
            app.tabBars.buttons.element(boundBy: i).tap()
            sleep(1)
            try app.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: "/tmp/shot-tab-\(names[i]).png"))
        }
    }

    /// Captures Home, the add sheet and the detail screen in whatever
    /// appearance the simulator is set to, so light and dark can be reviewed
    /// side by side. The files are renamed after each run (see
    /// `xcrun simctl ui booted appearance`).
    func testThemeScreenshots() throws {
        func shot(_ name: String) throws {
            try XCUIScreen.main.screenshot().pngRepresentation
                .write(to: URL(fileURLWithPath: "/tmp/theme-\(name).png"))
        }

        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["addBattery"].waitForExistence(timeout: 15))
        sleep(1)
        try shot("home")

        app.buttons["addBattery"].tap()
        sleep(4)
        try shot("add")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.buttons["addBattery"].waitForExistence(timeout: 15))
        app.terminate()

        let real = launchWithFakeBattery()
        XCTAssertTrue(real.buttons["batteryCard"].waitForExistence(timeout: 15))
        sleep(1)
        try shot("home-real")
        real.buttons["batteryCard"].tap()
        // The detail starts in the connecting state, which draws the
        // dashboard skeleton while it waits for the BMS.
        sleep(2)
        try shot("detail")
        XCTAssertTrue(real.tabBars.buttons.element(boundBy: 3).waitForExistence(timeout: 15))
        real.tabBars.buttons.element(boundBy: 3).tap()
        sleep(1)
        try shot("control")
    }

    /// The detail `...` menu in the current simulator appearance, so the menu
    /// item colors and its toolbar icon can be checked in both modes.
    func testThemeMenuScreenshot() throws {
        let app = launchWithFakeBattery()
        XCTAssertTrue(app.buttons["batteryCard"].waitForExistence(timeout: 15))
        app.buttons["batteryCard"].tap()
        XCTAssertTrue(app.buttons["moreMenu"].waitForExistence(timeout: 15))
        app.buttons["moreMenu"].tap()
        sleep(2)
        try XCUIScreen.main.screenshot().pngRepresentation
            .write(to: URL(fileURLWithPath: "/tmp/theme-menu.png"))
    }

    func testBatFlapScreenshots() throws {
        for (arg, file) in [([], "/tmp/shot-bat-rest.png"), (["UITEST_BAT_UP"], "/tmp/shot-bat-up.png")] {
            let app = XCUIApplication()
            app.launchArguments = arg
            app.launch()
            XCTAssertTrue(app.buttons["addBattery"].waitForExistence(timeout: 15))
            sleep(1)
            try app.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: file))
            app.terminate()
        }
    }
}
