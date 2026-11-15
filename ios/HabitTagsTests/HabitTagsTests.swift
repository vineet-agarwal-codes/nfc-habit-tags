import XCTest
import UIKit
import HabitCore
@testable import HabitTags

final class HabitTagsTests: XCTestCase {
    private func engine() throws -> AppEngine {
        let url = try XCTUnwrap(Bundle(for: AppModel.self).url(forResource: "catalog", withExtension: "json"))
        let catalog = try String(contentsOf: url, encoding: .utf8)
        return AppEngine(stateJson: nil, bundledCatalogJson: catalog, cachedSnapshotJson: nil, newDeviceId: "00000000-0000-0000-0000-000000000002")
    }

    /// The shared Kotlin core works through the Swift bridge: catalog, install, start and the outside-hours path.
    func testEngineThroughSwift() throws {
        let e = try engine()
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        XCTAssertFalse(e.discover(nowMs: now, tzId: "Asia/Kolkata").isEmpty)
        e.install(recipeId: "desk-deep-work")
        XCTAssertTrue(e.isInstalled(id: "desk-deep-work"))
        let out = e.startManually(recipeId: "desk-deep-work", force: false, nowMs: now, tzId: "Asia/Kolkata")
        XCTAssertEqual(out.kind, "started")
        XCTAssertEqual(e.activeModes(nowMs: now, tzId: "Asia/Kolkata").count, 1)
        let tagId = e.newTagId()
        XCTAssertEqual(e.tagIdFromUrl(url: e.tagUrl(tagId: tagId)), tagId)
        XCTAssertFalse(AppEngine.Companion.shared.isReadableState(text: "garbage"))
    }

    /// Dictation, QuickType suggestions and paste insert several characters at once; typing inserts one.
    func testGateRejectsBulkInsertAndPaste() {
        var text = ""
        var rejected = false
        let gate = GateTextView(text: Binding(get: { text }, set: { text = $0 }), rejected: Binding(get: { rejected }, set: { rejected = $0 }))
        let coordinator = gate.makeCoordinator()
        let view = NoPasteTextView()
        XCTAssertFalse(coordinator.textView(view, shouldChangeTextIn: NSRange(location: 0, length: 0), replacementText: "abcdef"))
        XCTAssertTrue(rejected)
        XCTAssertTrue(coordinator.textView(view, shouldChangeTextIn: NSRange(location: 0, length: 0), replacementText: "abc"))
        XCTAssertFalse(rejected)
        XCTAssertFalse(view.canPerformAction(#selector(UIResponderStandardEditActions.paste(_:)), withSender: nil))
    }
}
