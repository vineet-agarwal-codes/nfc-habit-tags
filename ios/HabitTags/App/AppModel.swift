import Foundation
import SwiftUI
import BackgroundTasks
import HabitCore

struct ChecklistItem: Identifiable {
    let id = UUID()
    let title: String
    let items: [String]
}

struct SetupTag: Identifiable { let id: String }

struct GateRequest: Identifiable {
    let id: String
    let title: String
}

/** Thin platform shell around the shared [AppEngine]: persistence, network, notifications and NFC. */
@MainActor
final class AppModel: ObservableObject {
    static let shared = AppModel()
    static let reportTaskId = "com.habittags.app.report"

    @Published private(set) var tick = 0
    @Published var banner: String?
    @Published var checklist: ChecklistItem?
    @Published var setupTag: SetupTag?
    @Published var gate: GateRequest?

    let engine: AppEngine
    let uiTest: Bool
    let gateWords: Int32
    private let stateURL: URL
    private let cacheURL: URL
    private let backend = SupabaseBackend()
    private lazy var realtime = RealtimeWatcher { [weak self] in
        Task { @MainActor in await self?.refreshCatalog() }
    }
    private let tags = TagSession()
    private var reporting = false

    var nowMs: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
    var tz: String { TimeZone.current.identifier }
    var nfcAvailable: Bool { !uiTest && TagSession.available }

    private init() {
        let args = ProcessInfo.processInfo.arguments
        // Tests never touch the real backend (no network, no usage reports from CI devices).
        uiTest = args.contains("-uitest") || ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
        gateWords = args.firstIndex(of: "-gateWords").flatMap { args.indices.contains($0 + 1) ? Int32(args[$0 + 1]) : nil } ?? 100
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("HabitTags", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        stateURL = dir.appendingPathComponent(uiTest ? "state-uitest.json" : "state.json")
        cacheURL = dir.appendingPathComponent("catalog_cache.json")
        if uiTest { try? FileManager.default.removeItem(at: stateURL) }

        let bundled = Bundle.main.url(forResource: "catalog", withExtension: "json")
            .flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? "{\"recipes\":[],\"appGroups\":[]}"
        var stateText = try? String(contentsOf: stateURL, encoding: .utf8)
        if let text = stateText, !text.isEmpty, !AppEngine.Companion.shared.isReadableState(text: text) {
            // Never overwrite a state file we can't read; keep it aside for recovery.
            let aside = dir.appendingPathComponent("state.json.bad-\(Int64(Date().timeIntervalSince1970 * 1000))")
            try? FileManager.default.moveItem(at: stateURL, to: aside)
            stateText = nil
        }
        let cache = uiTest ? nil : (try? String(contentsOf: cacheURL, encoding: .utf8))
        engine = AppEngine(stateJson: stateText, bundledCatalogJson: bundled, cachedSnapshotJson: cache, newDeviceId: UUID().uuidString.lowercased())
        save()
    }

    // MARK: - Lifecycle

    func onForeground() {
        runEffects(engine.expire(nowMs: nowMs))
        changed()
        guard !uiTest else { return }
        realtime.start()
        Task {
            await refreshCatalog()
            await reportIfDue()
        }
        scheduleBackgroundReport()
    }

    func onBackground() {
        realtime.stop()
    }

    func changed() {
        save()
        tick += 1
    }

    private func save() {
        try? engine.stateJson().write(to: stateURL, atomically: true, encoding: .utf8)
    }

    // MARK: - Catalog and reports

    func refreshCatalog() async {
        guard !uiTest, let remote = try? await backend.fetchCatalog() else { return }
        if engine.applyRemote(recipesJson: remote.recipes, groupsJson: remote.groups, statsJson: remote.stats, nowMs: nowMs) {
            try? engine.snapshotJson().write(to: cacheURL, atomically: true, encoding: .utf8)
            changed()
        }
    }

    func reportIfDue() async {
        guard !uiTest, !reporting, engine.reportDue(nowMs: nowMs) else { return }
        reporting = true
        defer { reporting = false }
        let body = engine.buildReportBody(nowMs: nowMs, tzId: tz)
        if (try? await backend.report(body: body)) != nil {
            engine.reportSent(nowMs: nowMs)
            save()
        }
    }

    func scheduleBackgroundReport() {
        let request = BGAppRefreshTaskRequest(identifier: Self.reportTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 6 * 60 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    // MARK: - Taps and starts

    func handle(url: URL) {
        if let id = engine.tagIdFromUrl(url: url.absoluteString) { tap(tagId: id) }
    }

    func tap(tagId: String) {
        let out = engine.tap(tagId: tagId, nowMs: nowMs, tzId: tz)
        if out.kind == "unknownTag" {
            setupTag = SetupTag(id: tagId)
        } else {
            apply(out, showMessage: true)
        }
    }

    /** Outside a recipe's hours this returns the outcome without starting, so the caller can offer "Start anyway". */
    @discardableResult
    func startNow(recipeId: String, force: Bool) -> TapOutcome {
        Notifications.requestAuthorization()
        let out = engine.startManually(recipeId: recipeId, force: force, nowMs: nowMs, tzId: tz)
        apply(out, showMessage: out.kind != "outsideHours")
        return out
    }

    func endViaGate(modeKey: String) {
        engine.endViaGate(modeKey: modeKey)
        Notifications.cancel(id: "autooff-\(modeKey)")
        changed()
    }

    private func apply(_ out: TapOutcome, showMessage: Bool) {
        runEffects(out.effects)
        switch out.kind {
        case "started" where out.autoOffAtMs > 0:
            Notifications.schedule(
                id: "autooff-\(out.modeKey)",
                title: "\(out.title) ended",
                body: "Open Habit Tags to finish it.",
                at: Date(timeIntervalSince1970: TimeInterval(out.autoOffAtMs) / 1000)
            )
        case "ended":
            Notifications.cancel(id: "autooff-\(out.modeKey)")
        default:
            break
        }
        if showMessage, !out.message.isEmpty { banner = out.message }
        changed()
    }

    private func runEffects(_ effects: [EffectView]) {
        for effect in effects {
            switch effect.kind {
            case "checklist":
                checklist = ChecklistItem(title: effect.title, items: effect.items)
            case "timer":
                Notifications.requestAuthorization()
                Notifications.schedule(
                    id: "timer-\(UUID().uuidString)",
                    title: "Timer done",
                    body: effect.title,
                    at: Date(timeIntervalSinceNow: TimeInterval(effect.minutes) * 60)
                )
            case "openUri":
                if let url = URL(string: effect.uri) { UIApplication.shared.open(url) }
            default:
                break
            }
        }
    }

    // MARK: - Installing and editing

    func install(recipeId: String) { engine.install(recipeId: recipeId); changed() }

    func setParam(recipeId: String, key: String, value: Int32) {
        engine.setParam(recipeId: recipeId, key: key, value: value)
        changed()
    }

    func link(tagId: String, recipeId: String) { engine.linkTag(tagId: tagId, recipeId: recipeId); changed() }

    func removeTag(tagId: String) { engine.removeTag(tagId: tagId); changed() }

    func uninstall(recipeId: String) -> Bool {
        let done = engine.uninstall(recipeId: recipeId)
        changed()
        return done
    }

    // MARK: - NFC

    func writeTag(recipeId: String) {
        let id = engine.createTagId()
        tags.write(url: engine.tagUrl(tagId: id)) { [weak self] result in
            guard let self else { return }
            switch result {
            case .success:
                self.link(tagId: id, recipeId: recipeId)
                self.banner = "Tag ready. Tap it to start."
            case .failure(let error):
                if let text = error.message { self.banner = text }
            }
        }
    }

    func scanTag() {
        tags.read { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(let raw):
                let id: String?
                switch raw {
                case .url(let url): id = self.engine.tagIdFromUrl(url: url)
                case .text(let text): id = self.engine.tagIdFromText(text: text)
                }
                if let id { self.tap(tagId: id) } else { self.banner = "This isn't a Habit Tags tag." }
            case .failure(let error):
                if let text = error.message { self.banner = text }
            }
        }
    }
}
