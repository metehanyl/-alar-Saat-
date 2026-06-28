import Foundation
import UserNotifications

/// iOS has no equivalent of Android's `AlarmManager.setAlarmClock()` — there is
/// no API for a third-party app to guarantee waking the device and bypassing
/// silent mode/Focus the way the system Clock app does. This scheduler uses
/// `UNUserNotificationCenter` local notifications instead: a custom sound
/// (the pre-rendered ~28s melody loop) plays once when the notification fires,
/// and tapping it opens `AlarmRingView` for PIN entry / snooze / dismiss. This
/// is the closest reliable approximation available without a special
/// Apple alarm entitlement.
enum AlarmScheduler {
    static let snoozeMinutes = 5

    private static func identifier(alarmId: UUID, suffix: String) -> String {
        "alarm-\(alarmId.uuidString)-\(suffix)"
    }

    /// Schedules one notification request per matching weekday (repeating) or a
    /// single one-shot request (one-time). Returns the next trigger date for
    /// display in the UI, mirroring Android's `schedule()` return value.
    @discardableResult
    static func schedule(_ alarm: AlarmEntity) async -> Date? {
        cancel(alarm)
        guard alarm.enabled else { return nil }

        let center = UNUserNotificationCenter.current()
        let days = alarm.repeatDays

        if days.isEmpty {
            let triggerDate = computeNextTriggerDate(hour: alarm.hour, minute: alarm.minute, repeatDays: [])
            let request = makeRequest(
                identifier: identifier(alarmId: alarm.id, suffix: "once"),
                alarm: alarm,
                dateComponents: Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: triggerDate),
                repeats: false
            )
            try? await center.add(request)
            return triggerDate
        }

        for weekday in days {
            var components = DateComponents()
            components.hour = alarm.hour
            components.minute = alarm.minute
            components.weekday = weekday
            let request = makeRequest(
                identifier: identifier(alarmId: alarm.id, suffix: "day-\(weekday)"),
                alarm: alarm,
                dateComponents: components,
                repeats: true
            )
            try? await center.add(request)
        }
        return computeNextTriggerDate(hour: alarm.hour, minute: alarm.minute, repeatDays: days)
    }

    static func cancel(_ alarm: AlarmEntity) {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { requests in
            let ids = requests
                .map(\.identifier)
                .filter { $0.hasPrefix(alarm.notificationIdPrefix) }
            center.removePendingNotificationRequests(withIdentifiers: ids)
        }
    }

    @discardableResult
    static func scheduleSnooze(
        alarmId: UUID,
        label: String,
        soundId: Int,
        requirePin: Bool,
        pinHash: String?,
        minutes: Int = snoozeMinutes
    ) async -> Date {
        let triggerDate = Date().addingTimeInterval(TimeInterval(minutes * 60))
        let content = UNMutableNotificationContent()
        content.title = label.isEmpty ? "Alarm" : label
        content.body = "Kapatmak için dokunun"
        content.sound = UNNotificationSound(named: UNNotificationSoundName("\(AlarmSounds.byId(soundId).fileBaseName).wav"))
        content.categoryIdentifier = NotificationManager.alarmCategoryId
        content.interruptionLevel = .timeSensitive
        content.userInfo = [
            "alarmId": alarmId.uuidString,
            "label": label,
            "soundId": soundId,
            "requirePin": requirePin,
            "pinHash": pinHash ?? "",
            "isSnooze": true,
        ]
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: TimeInterval(minutes * 60), repeats: false)
        let request = UNNotificationRequest(
            identifier: identifier(alarmId: alarmId, suffix: "snooze"),
            content: content,
            trigger: trigger
        )
        try? await UNUserNotificationCenter.current().add(request)
        return triggerDate
    }

    private static func makeRequest(
        identifier: String,
        alarm: AlarmEntity,
        dateComponents: DateComponents,
        repeats: Bool
    ) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = alarm.label.isEmpty ? "Alarm" : alarm.label
        content.body = "Kapatmak için dokunun"
        content.sound = UNNotificationSound(named: UNNotificationSoundName("\(AlarmSounds.byId(alarm.soundId).fileBaseName).wav"))
        content.categoryIdentifier = NotificationManager.alarmCategoryId
        content.interruptionLevel = .timeSensitive
        content.userInfo = [
            "alarmId": alarm.id.uuidString,
            "label": alarm.label,
            "soundId": alarm.soundId,
            "requirePin": alarm.requirePin,
            "pinHash": alarm.pinHash ?? "",
            "isSnooze": false,
        ]
        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: repeats)
        return UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
    }

    /// Mirrors Android's `computeNextTriggerMillis`: next occurrence of hour:minute
    /// today (if still ahead) or tomorrow for one-time alarms; for repeating alarms,
    /// the next matching weekday strictly after now.
    static func computeNextTriggerDate(hour: Int, minute: Int, repeatDays: Set<Int>) -> Date {
        let calendar = Calendar.current
        let now = Date()
        var candidateComponents = calendar.dateComponents([.year, .month, .day], from: now)
        candidateComponents.hour = hour
        candidateComponents.minute = minute
        candidateComponents.second = 0
        guard var candidate = calendar.date(from: candidateComponents) else { return now }

        if repeatDays.isEmpty {
            if candidate <= now {
                candidate = calendar.date(byAdding: .day, value: 1, to: candidate) ?? candidate
            }
            return candidate
        }

        for offset in 0...7 {
            guard let attempt = calendar.date(byAdding: .day, value: offset, to: candidate) else { continue }
            let weekday = calendar.component(.weekday, from: attempt)
            if repeatDays.contains(weekday) && attempt > now {
                return attempt
            }
        }
        return calendar.date(byAdding: .day, value: 7, to: candidate) ?? candidate
    }
}
