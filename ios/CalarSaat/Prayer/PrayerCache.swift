import Foundation

/// Mirrors Android's `PrayerCache`: stores the last fetched `PrayerBundle` as
/// JSON in `UserDefaults` so prayer times survive app restarts without a
/// network round-trip.
enum PrayerCache {
    private static let defaults = UserDefaults(suiteName: "calar_saat_prayer_cache") ?? .standard
    private static let key = "prayer_bundle"

    static func load() -> PrayerBundle? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(PrayerBundle.self, from: data)
    }

    static func save(_ bundle: PrayerBundle) {
        guard let data = try? JSONEncoder().encode(bundle) else { return }
        defaults.set(data, forKey: key)
    }
}
