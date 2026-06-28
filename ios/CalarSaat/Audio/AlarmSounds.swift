import Foundation

struct AlarmTone {
    /// Hz; <= 0 means silence.
    let frequencyHz: Int
    let durationMs: Int
}

struct AlarmMelody: Identifiable {
    let id: Int
    let nameKey: String
    let tones: [AlarmTone]
    let tailSilenceMs: Int

    /// Filename (without extension) of the pre-rendered ~28s loop bundled as a
    /// notification sound. Must match `Resources/Sounds/<fileBaseName>.wav`.
    var fileBaseName: String { nameKey }
}

/// Mirrors Android's `AlarmSounds` melody table exactly (frequencies/durations).
enum AlarmSounds {
    static let all: [AlarmMelody] = [
        AlarmMelody(
            id: 0,
            nameKey: "melody_classic",
            tones: [
                AlarmTone(frequencyHz: 1000, durationMs: 150),
                AlarmTone(frequencyHz: 0, durationMs: 80),
                AlarmTone(frequencyHz: 1000, durationMs: 150),
                AlarmTone(frequencyHz: 0, durationMs: 80),
                AlarmTone(frequencyHz: 750, durationMs: 220),
            ],
            tailSilenceMs: 350
        ),
        AlarmMelody(
            id: 1,
            nameKey: "melody_digital",
            tones: [
                AlarmTone(frequencyHz: 1300, durationMs: 90),
                AlarmTone(frequencyHz: 0, durationMs: 90),
                AlarmTone(frequencyHz: 1300, durationMs: 90),
                AlarmTone(frequencyHz: 0, durationMs: 90),
                AlarmTone(frequencyHz: 1300, durationMs: 90),
                AlarmTone(frequencyHz: 0, durationMs: 90),
                AlarmTone(frequencyHz: 1300, durationMs: 90),
            ],
            tailSilenceMs: 400
        ),
        AlarmMelody(
            id: 2,
            nameKey: "melody_triplet",
            tones: [
                AlarmTone(frequencyHz: 800, durationMs: 130),
                AlarmTone(frequencyHz: 0, durationMs: 60),
                AlarmTone(frequencyHz: 900, durationMs: 130),
                AlarmTone(frequencyHz: 0, durationMs: 60),
                AlarmTone(frequencyHz: 1050, durationMs: 130),
            ],
            tailSilenceMs: 450
        ),
        AlarmMelody(
            id: 3,
            nameKey: "melody_rising",
            tones: [
                AlarmTone(frequencyHz: 600, durationMs: 90),
                AlarmTone(frequencyHz: 750, durationMs: 90),
                AlarmTone(frequencyHz: 900, durationMs: 90),
                AlarmTone(frequencyHz: 1050, durationMs: 90),
                AlarmTone(frequencyHz: 1200, durationMs: 110),
            ],
            tailSilenceMs: 400
        ),
        AlarmMelody(
            id: 4,
            nameKey: "melody_siren",
            tones: [
                AlarmTone(frequencyHz: 1100, durationMs: 300),
                AlarmTone(frequencyHz: 750, durationMs: 300),
                AlarmTone(frequencyHz: 1100, durationMs: 300),
                AlarmTone(frequencyHz: 750, durationMs: 300),
            ],
            tailSilenceMs: 250
        ),
    ]

    static func byId(_ id: Int) -> AlarmMelody {
        all.first { $0.id == id } ?? all[0]
    }

    static func displayName(_ key: String) -> String {
        switch key {
        case "melody_classic": return "Klasik Çalar Saat"
        case "melody_digital": return "Dijital Atış"
        case "melody_triplet": return "Acil Üçleme"
        case "melody_rising": return "Yükselen Alarm"
        case "melody_siren": return "Siren"
        default: return key
        }
    }
}
