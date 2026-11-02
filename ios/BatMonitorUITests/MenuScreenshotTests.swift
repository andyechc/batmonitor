import XCTest

/// Opens screens and writes screenshots to /tmp so UI can be reviewed
/// without tapping on a physical device.
final class MenuScreenshotTests: XCTestCase {
    func testDemoMenuScreenshot() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["openDemo"].waitForExistence(timeout: 15))
        app.buttons["openDemo"].tap()
        XCTAssertTrue(app.buttons["moreMenu"].waitForExistence(timeout: 15))
        app.buttons["moreMenu"].tap()
        sleep(2)
        let shot = app.screenshot()
        try shot.pngRepresentation.write(to: URL(fileURLWithPath: "/tmp/menu-shot-demo.png"))
    }

    func testRealMenuScreenshot() throws {
        let app = XCUIApplication()
        app.launchArguments = ["UITEST_FAKE_BATTERY"]
        app.launch()
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

    func testDemoTabsScreenshots() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["openDemo"].waitForExistence(timeout: 15))
        app.buttons["openDemo"].tap()
        XCTAssertTrue(app.tabBars.buttons.element(boundBy: 0).waitForExistence(timeout: 15))
        let names = ["overview", "cells", "status", "control"]
        for i in 0 ..< 4 {
            app.tabBars.buttons.element(boundBy: i).tap()
            sleep(1)
            try app.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: "/tmp/shot-tab-\(names[i]).png"))
        }
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
