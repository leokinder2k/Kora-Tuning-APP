import XCTest

final class KoraParityUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
    }

    func testMainFeatureTabsRender() throws {
        assertVisibleText("Kora Tuning")
        assertVisibleText("Setup")
        attachScreenshot(named: "tab-instrument")

        openTabAndCapture("Scale", screenshotName: "tab-scale")
        openTabAndCapture("Overview", screenshotName: "tab-overview")
        openTabAndCapture("Tuner", screenshotName: "tab-tuner")
        openTabAndCapture("Presets", screenshotName: "tab-presets")
        openTabAndCapture("Notation", screenshotName: "tab-notation")
    }

    func testSettingsAndAboutRender() throws {
        assertVisibleText("Kora Tuning")
        tapFirstMatching(label: "Settings")
        tapStaticText(label: "Settings")
        assertVisibleText("App theme")
        assertVisibleText("App language")
        tapFirstMatching(label: "OK")

        tapFirstMatching(label: "Settings")
        tapStaticText(label: "About")
        assertVisibleText("A tuning companion")
        assertVisibleText("Privacy Policy")
        tapFirstMatching(label: "OK")
    }

    func testOverviewModesDoNotCrashAndCaptureScreenshots() throws {
        openTab("Overview", expecting: "Instant Overview")
        attachScreenshot(named: "overview-diagram")

        tapOverviewModeChip(index: 1)
        assertVisibleText("Instant Overview")
        attachScreenshot(named: "overview-table")

        tapOverviewModeChip(index: 2)
        assertVisibleText("Instant Overview")
        attachScreenshot(named: "overview-chords")

        tapOverviewModeChip(index: 3)
        assertVisibleText("Instant Overview")
        attachScreenshot(named: "overview-exercise")
    }

    func testNotationFilePickerOpens() throws {
        tapFirstMatching(label: "Notation")
        assertVisibleText("Import Score")
        attachScreenshot(named: "notation-import")
        if !tryTapFirstMatching(label: "Open file…", timeout: 2) &&
            !tryTapFirstMatching(label: "Open file", timeout: 1) {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.28, dy: 0.46)).tap()
        }
        XCTAssertTrue(
            app.buttons["Cancel"].waitForExistence(timeout: 10) ||
                app.staticTexts["Browse"].waitForExistence(timeout: 1) ||
                app.staticTexts["Recents"].waitForExistence(timeout: 1),
            "Expected iOS document picker to appear"
        )
        attachScreenshot(named: "notation-file-picker")
        if app.buttons["Cancel"].exists {
            app.buttons["Cancel"].tap()
        }
    }

    func testTunerStartActionIsReachable() throws {
        addUIInterruptionMonitor(withDescription: "Microphone Permission") { alert in
            if alert.buttons["Allow"].exists {
                alert.buttons["Allow"].tap()
                return true
            }
            if alert.buttons["OK"].exists {
                alert.buttons["OK"].tap()
                return true
            }
            return false
        }

        tapFirstMatching(label: "Tuner")
        assertVisibleText("Kora Tuning")
        scrollUntilCanTap(label: "Start Live Tuner", maxSwipes: 6, timeout: 0.75)
        app.tap()
        attachScreenshot(named: "tuner-after-start")

        if app.buttons["Stop Live Tuner"].waitForExistence(timeout: 5) {
            app.buttons["Stop Live Tuner"].tap()
        }
    }

    private func openTab(_ label: String, expecting expectedText: String) {
        tapFirstMatching(label: label)
        assertVisibleText(expectedText)
    }

    private func openTabAndCapture(_ label: String, screenshotName: String) {
        tapFirstMatching(label: label)
        assertVisibleText("Kora Tuning")
        attachScreenshot(named: screenshotName)
    }

    private func tapOverviewModeChip(index: Int) {
        let xPositions = [0.19, 0.38, 0.59, 0.82]
        app.coordinate(withNormalizedOffset: CGVector(dx: xPositions[index], dy: 0.19)).tap()
    }

    private func attachScreenshot(named name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func tapStaticText(label: String, timeout: TimeInterval = 8) {
        let element = app.staticTexts[label]
        XCTAssertTrue(element.waitForExistence(timeout: timeout), "Could not find static text labeled \(label)")
        if !element.isHittable {
            app.swipeUp()
        }
        element.tap()
    }

    private func tapFirstMatching(label: String, timeout: TimeInterval = 8) {
        if tryTapFirstMatching(label: label, timeout: timeout) {
            return
        }

        XCTFail("Could not tap element labeled \(label)")
    }

    @discardableResult
    private func scrollUntilCanTap(label: String, maxSwipes: Int, timeout: TimeInterval) -> Bool {
        if tryScrollUntilCanTap(label: label, maxSwipes: maxSwipes, timeout: timeout) {
            return true
        }

        XCTFail("Could not tap element labeled \(label)")
        return false
    }

    private func tryScrollUntilCanTap(label: String, maxSwipes: Int, timeout: TimeInterval) -> Bool {
        for attempt in 0...maxSwipes {
            if tryTapFirstMatching(label: label, timeout: timeout) {
                return true
            }
            if attempt < maxSwipes {
                app.swipeUp()
            }
        }

        return false
    }

    private func tryTapFirstMatching(label: String, timeout: TimeInterval = 8) -> Bool {
        let candidates: [XCUIElement] = [
            app.buttons[label],
            app.staticTexts[label],
            app.otherElements[label],
            app.descendants(matching: .any)[label],
        ]

        for element in candidates where element.waitForExistence(timeout: timeout) {
            if tapWhenHittable(element) {
                return true
            }
        }

        let predicate = NSPredicate(format: "label CONTAINS[c] %@", label)
        let matchingElement = app.descendants(matching: .any).matching(predicate).firstMatch
        if matchingElement.waitForExistence(timeout: 1) {
            if tapWhenHittable(matchingElement) {
                return true
            }
        }

        return false
    }

    private func tapWhenHittable(_ element: XCUIElement) -> Bool {
        for attempt in 0..<6 {
            if element.isHittable {
                element.tap()
                return true
            }
            if attempt < 5 {
                app.swipeUp()
            }
        }
        return false
    }

    private func assertVisibleText(_ text: String, timeout: TimeInterval = 8) {
        if app.staticTexts[text].waitForExistence(timeout: timeout) {
            return
        }

        let predicate = NSPredicate(format: "label CONTAINS[c] %@", text)
        if app.staticTexts.matching(predicate).firstMatch.waitForExistence(timeout: 1) {
            return
        }
        if app.descendants(matching: .any).matching(predicate).firstMatch.waitForExistence(timeout: 1) {
            return
        }

        XCTFail("Could not find visible text containing \(text)")
    }
}
