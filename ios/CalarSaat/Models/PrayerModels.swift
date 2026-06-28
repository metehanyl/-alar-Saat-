import Foundation

struct PrayerDay: Codable, Equatable {
    let tarih: String   // "dd.MM.yyyy"
    let imsak: String   // "HH:MM"
    let gunes: String
    let ogle: String
    let ikindi: String
    let aksam: String
    let yatsi: String
}

struct CityOption: Codable, Equatable {
    let id: String
    let name: String
}

struct DistrictOption: Codable, Equatable {
    let id: String
    let name: String
}

struct SelectedLocation: Codable, Equatable {
    let sehirId: String
    let sehirAdi: String
    let ilceId: String
    let ilceAdi: String
}

struct PrayerBundle: Codable, Equatable {
    let sehirId: String
    let sehirAdi: String
    let ilceId: String
    let ilceAdi: String
    let days: [PrayerDay]
    let fetchedAtEpochMillis: Int64

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd.MM.yyyy"
        formatter.locale = Locale(identifier: "tr_TR")
        return formatter
    }()

    /// Mirrors Android's `todayOrClosest()`: prefers the day matching today's
    /// date string; otherwise falls back to the last (or first) day available.
    func todayOrClosest() -> (day: PrayerDay, isToday: Bool)? {
        guard !days.isEmpty else { return nil }
        let todayString = Self.dateFormatter.string(from: Date())
        if let match = days.first(where: { $0.tarih == todayString }) {
            return (match, true)
        }
        return (days.last ?? days[0], false)
    }
}

enum RefreshResult {
    case success(PrayerBundle)
    case failure(String)
}

struct ImsakTime {
    let hour: Int
    let minute: Int
}
