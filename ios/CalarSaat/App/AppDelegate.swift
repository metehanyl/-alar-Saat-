import UIKit
import BackgroundTasks
import SwiftData

/// `BGTaskScheduler.register` must run before `application(_:didFinishLaunchingWithOptions:)`
/// returns, which SwiftUI's `App.init()`/`.task` timing doesn't guarantee. An
/// `AppDelegate` adaptor is the only reliable place for it.
final class AppDelegate: NSObject, UIApplicationDelegate {
    var modelContainer: ModelContainer?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        NotificationManager.shared.configure()

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: SabahNamaziManager.refreshTaskIdentifier,
            using: nil
        ) { [weak self] task in
            guard let self, let container = self.modelContainer else {
                task.setTaskCompleted(success: false)
                return
            }
            let context = ModelContext(container)
            SabahNamaziManager.handleBackgroundRefresh(task: task as! BGAppRefreshTask, context: context)
        }

        return true
    }
}
