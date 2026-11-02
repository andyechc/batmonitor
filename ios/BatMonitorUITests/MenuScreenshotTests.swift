import XCTest

/// Opens the detail menus and writes screenshots to /tmp so menu colors
/// can be verified without tapping on a physical device.
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
        app.buttons["batteryCard"].tap()
        XCTAssertTrue(app.buttons["moreMenu"].waitForExistence(timeout: 15))
        app.buttons["moreMenu"].tap()
        sleep(2)
        let shot = app.screenshot()
        try shot.pngRepresentation.write(to: URL(fileURLWithPath: "/tmp/menu-shot-real.png"))
    }
}
