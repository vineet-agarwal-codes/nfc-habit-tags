import XCTest

final class HabitTagsUITests: XCTestCase {
    /// Offline first launch → install Desk → Start now → On now strip → typing gate ends it.
    func testInstallStartAndEndViaGate() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-uitest", "-gateWords", "5"]
        app.launch()

        let desk = app.staticTexts["Desk: Deep Work"].firstMatch
        XCTAssertTrue(desk.waitForExistence(timeout: 10))
        desk.tap()
        app.buttons["Install"].tap()
        let start = app.buttons["Start now"]
        XCTAssertTrue(start.waitForExistence(timeout: 5))
        start.tap()
        let endNow = app.buttons["End now"]
        XCTAssertTrue(endNow.waitForExistence(timeout: 5))
        attach(app, "mode_on")

        endNow.tap()
        let passage = app.descendants(matching: .any)["gate-passage"]
        XCTAssertTrue(passage.waitForExistence(timeout: 5))
        attach(app, "gate")
        let input = app.textViews["gate-input"]
        input.tap()
        input.typeText(passage.label)
        let gone = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: endNow)
        wait(for: [gone], timeout: 10)
    }

    private func attach(_ app: XCUIApplication, _ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
    }
}
