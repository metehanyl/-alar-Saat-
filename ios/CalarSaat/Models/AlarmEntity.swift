import Foundation
import SwiftData

/// Mirrors the Android Room `alarms` table (schema v5) field-for-field.
@Model
final class AlarmEntity {
    @Attribute(.unique) var id: UUID
    var hour: Int
    var minute: Int
    var label: String
    /// Comma-separated `Calendar` weekday values (1=Sunday...7=Saturday). Empty = one-time alarm.
    var repeatDaysRaw: String
    var enabled: Bool
    var soundId: Int
    var nextTriggerAt: Date?
    var isAutoSabahNamazi: Bool
    var groupId: UUID?
    var requirePin: Bool
    var pinHash: String?

    init(
        id: UUID = UUID(),
        hour: Int,
        minute: Int,
        label: String,
        repeatDays: Set<Int> = [],
        enabled: Bool = true,
        soundId: Int = 0,
        nextTriggerAt: Date? = nil,
        isAutoSabahNamazi: Bool = false,
        groupId: UUID? = nil,
        requirePin: Bool = false,
        pinHash: String? = nil
    ) {
        self.id = id
        self.hour = hour
        self.minute = minute
        self.label = label
        self.repeatDaysRaw = Self.daysToString(repeatDays)
        self.enabled = enabled
        self.soundId = soundId
        self.nextTriggerAt = nextTriggerAt
        self.isAutoSabahNamazi = isAutoSabahNamazi
        self.groupId = groupId
        self.requirePin = requirePin
        self.pinHash = pinHash
    }

    var repeatDays: Set<Int> {
        get { Self.parseDays(repeatDaysRaw) }
        set { repeatDaysRaw = Self.daysToString(newValue) }
    }

    var isRepeating: Bool {
        !repeatDaysRaw.trimmingCharacters(in: .whitespaces).isEmpty
    }

    static func parseDays(_ raw: String) -> Set<Int> {
        let trimmed = raw.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return [] }
        return Set(trimmed.split(separator: ",").compactMap { Int($0) })
    }

    static func daysToString(_ days: Set<Int>) -> String {
        days.sorted().map(String.init).joined(separator: ",")
    }

    /// Notification request identifier prefix used by AlarmScheduler to group/cancel all
    /// pending requests (one per weekday for repeating alarms) belonging to this alarm.
    var notificationIdPrefix: String { "alarm-\(id.uuidString)" }
}
