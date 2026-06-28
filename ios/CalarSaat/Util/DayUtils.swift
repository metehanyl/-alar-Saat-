import Foundation

/// Maps `Calendar.Component.weekday` (1=Sun...7=Sat) to Turkish display labels.
/// Same convention as `EKCalendar`/`DateComponents.weekday`, so no conversion
/// from Android's `Calendar.DAY_OF_WEEK` is needed.
enum DayUtils {
    static let orderedDays: [(Int, String)] = [
        (2, "Pzt"), (3, "Sal"), (4, "Çar"), (5, "Per"), (6, "Cum"), (7, "Cmt"), (1, "Paz"),
    ]

    static func summarize(_ days: Set<Int>) -> String {
        if days.isEmpty { return "Bir kez" }
        if days.count == 7 { return "Her gün" }
        let weekdays: Set<Int> = [2, 3, 4, 5, 6]
        let weekend: Set<Int> = [1, 7]
        if days == weekdays { return "Hafta içi" }
        if days == weekend { return "Hafta sonu" }
        return orderedDays.filter { days.contains($0.0) }.map(\.1).joined(separator: ", ")
    }
}
