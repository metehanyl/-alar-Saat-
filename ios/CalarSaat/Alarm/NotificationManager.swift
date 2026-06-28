import Foundation
import UserNotifications
import Combine

/// Payload describing a fired alarm, parsed from a notification's `userInfo`.
/// Published by `NotificationManager.shared` so `CalarSaatApp` can present
/// `AlarmRingView` full-screen, regardless of whether the notification was
/// tapped (cold/background launch) or delivered while the app was foregrounded.
struct RingingAlarm: Identifiable, Equatable {
    let id: UUID
    let label: String
    let soundId: Int
    let requirePin: Bool
    let pinHash: String?
    let isSnooze: Bool
}

final class NotificationManager: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationManager()
    static let alarmCategoryId = "ALARM_CATEGORY"
    static let dismissActionId = "ALARM_DISMISS"
    static let snoozeActionId = "ALARM_SNOOZE"

    @Published var activeRingingAlarm: RingingAlarm?

    func configure() {
        UNUserNotificationCenter.current().delegate = self
        let dismiss = UNNotificationAction(identifier: Self.dismissActionId, title: "Kapat", options: [.authenticationRequired])
        let snooze = UNNotificationAction(identifier: Self.snoozeActionId, title: "Ertele (5 dk)", options: [])
        let category = UNNotificationCategory(
            identifier: Self.alarmCategoryId,
            actions: [dismiss, snooze],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    func requestAuthorizationIfNeeded() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        guard settings.authorizationStatus == .notDetermined else { return }
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
    }

    /// Notification arrived while app is in the foreground: surface the ring screen immediately
    /// instead of just showing a banner, since that's the alarm-clock behavior users expect.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        handle(userInfo: notification.request.content.userInfo)
        completionHandler([.banner, .sound])
    }

    /// Notification tapped, or a Dismiss/Snooze action button pressed.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        switch response.actionIdentifier {
        case Self.snoozeActionId:
            if let alarm = parse(userInfo) {
                Task {
                    await AlarmScheduler.scheduleSnooze(
                        alarmId: alarm.id,
                        label: alarm.label,
                        soundId: alarm.soundId,
                        requirePin: alarm.requirePin,
                        pinHash: alarm.pinHash
                    )
                }
            }
        case Self.dismissActionId:
            break
        default:
            handle(userInfo: userInfo)
        }
        completionHandler()
    }

    private func handle(userInfo: [AnyHashable: Any]) {
        guard let alarm = parse(userInfo) else { return }
        DispatchQueue.main.async {
            self.activeRingingAlarm = alarm
        }
    }

    private func parse(_ userInfo: [AnyHashable: Any]) -> RingingAlarm? {
        guard
            let idString = userInfo["alarmId"] as? String,
            let id = UUID(uuidString: idString)
        else { return nil }
        let pinHashRaw = userInfo["pinHash"] as? String
        return RingingAlarm(
            id: id,
            label: userInfo["label"] as? String ?? "",
            soundId: userInfo["soundId"] as? Int ?? 0,
            requirePin: userInfo["requirePin"] as? Bool ?? false,
            pinHash: (pinHashRaw?.isEmpty ?? true) ? nil : pinHashRaw,
            isSnooze: userInfo["isSnooze"] as? Bool ?? false
        )
    }
}
