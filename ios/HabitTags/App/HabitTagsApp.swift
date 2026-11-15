import SwiftUI
import BackgroundTasks

@main
struct HabitTagsApp: App {
    @StateObject private var model = AppModel.shared
    @Environment(\.scenePhase) private var scenePhase

    init() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: AppModel.reportTaskId, using: nil) { task in
            Task { @MainActor in
                await AppModel.shared.reportIfDue()
                AppModel.shared.scheduleBackgroundReport()
                task.setTaskCompleted(success: true)
            }
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .onOpenURL { model.handle(url: $0) }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL { model.handle(url: url) }
                }
        }
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .active: model.onForeground()
            case .background: model.onBackground()
            default: break
            }
        }
    }
}
