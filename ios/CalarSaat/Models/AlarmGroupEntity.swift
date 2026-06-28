import Foundation
import SwiftData

/// Mirrors the Android Room `alarm_groups` table. Purely a named container —
/// member alarms reference it via `AlarmEntity.groupId` and share melody/PIN settings.
@Model
final class AlarmGroupEntity {
    @Attribute(.unique) var id: UUID
    var name: String

    init(id: UUID = UUID(), name: String) {
        self.id = id
        self.name = name
    }
}
