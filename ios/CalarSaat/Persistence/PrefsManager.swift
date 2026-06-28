import Foundation

/// Mirrors Android's `PrefsManager` (SharedPreferences `alarm_prefs`) using `UserDefaults`.
final class PrefsManager {
    static let shared = PrefsManager()

    private let defaults = UserDefaults.standard

    private enum Key {
        static let pinHash = "pin_hash"
        static let sabahNamaziEnabled = "sabah_namazi_enabled"
        static let sabahNamaziCount = "sabah_namazi_count"
        static let sabahNamaziInterval = "sabah_namazi_interval"
        static let sabahNamaziOffset = "sabah_namazi_offset"
        static let sabahNamaziRequirePin = "sabah_namazi_require_pin"
        static let sabahNamaziPinHash = "sabah_namazi_pin_hash"
    }

    // MARK: Global PIN

    var isPinSet: Bool { defaults.string(forKey: Key.pinHash) != nil }

    func setPin(_ pin: String) {
        defaults.set(PinHasher.hash(pin), forKey: Key.pinHash)
    }

    func verifyPin(_ pin: String) -> Bool {
        PinHasher.verify(pin, against: defaults.string(forKey: Key.pinHash))
    }

    // MARK: Sabah Namazı settings

    var isSabahNamaziEnabled: Bool {
        get { defaults.bool(forKey: Key.sabahNamaziEnabled) }
        set { defaults.set(newValue, forKey: Key.sabahNamaziEnabled) }
    }

    var sabahNamaziAlarmCount: Int {
        get { defaults.object(forKey: Key.sabahNamaziCount) as? Int ?? 3 }
        set { defaults.set(newValue, forKey: Key.sabahNamaziCount) }
    }

    var sabahNamaziIntervalMinutes: Int {
        get { defaults.object(forKey: Key.sabahNamaziInterval) as? Int ?? 4 }
        set { defaults.set(newValue, forKey: Key.sabahNamaziInterval) }
    }

    var sabahNamaziOffsetMinutes: Int {
        get { defaults.object(forKey: Key.sabahNamaziOffset) as? Int ?? 10 }
        set { defaults.set(newValue, forKey: Key.sabahNamaziOffset) }
    }

    var isSabahNamaziPinRequired: Bool {
        get { defaults.bool(forKey: Key.sabahNamaziRequirePin) }
        set { defaults.set(newValue, forKey: Key.sabahNamaziRequirePin) }
    }

    var sabahNamaziPinHash: String? {
        get { defaults.string(forKey: Key.sabahNamaziPinHash) }
        set { defaults.set(newValue, forKey: Key.sabahNamaziPinHash) }
    }

    func setSabahNamaziPin(_ pin: String) {
        sabahNamaziPinHash = PinHasher.hash(pin)
    }
}
