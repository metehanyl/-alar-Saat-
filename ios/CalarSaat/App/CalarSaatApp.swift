import SwiftUI
import SwiftData

@main
struct CalarSaatApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    let modelContainer: ModelContainer

    init() {
        let schema = Schema([AlarmEntity.self, AlarmGroupEntity.self])
        modelContainer = try! ModelContainer(for: schema)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .modelContainer(modelContainer)
                .onAppear { appDelegate.modelContainer = modelContainer }
        }
    }
}

private struct RootView: View {
    @StateObject private var notifications = NotificationManager.shared

    var body: some View {
        MainView()
            .fullScreenCover(item: $notifications.activeRingingAlarm) { alarm in
                AlarmRingView(alarm: alarm) {
                    notifications.activeRingingAlarm = nil
                }
            }
    }
}
