import Foundation

/// Community mirror of Diyanet prayer times (ezanvakti.emushaf.net), same
/// endpoints as the Android `DiyanetApi`.
enum DiyanetApi {
    private static let baseURL = "https://ezanvakti.emushaf.net"
    private static let turkeyCountryId = "2"

    private struct RawCity: Decodable { let SehirID: String; let SehirAdi: String }
    private struct RawDistrict: Decodable { let IlceID: String; let IlceAdi: String }
    private struct RawPrayerDay: Decodable {
        let MiladiTarihKisa: String
        let Imsak: String
        let Gunes: String
        let Ogle: String
        let Ikindi: String
        let Aksam: String
        let Yatsi: String
    }

    private static func session() -> URLSession {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 10
        let session = URLSession(configuration: config)
        return session
    }

    static func getCities() async throws -> [CityOption] {
        let url = URL(string: "\(baseURL)/sehirler/\(turkeyCountryId)")!
        let (data, _) = try await session().data(from: url)
        let raw = try JSONDecoder().decode([RawCity].self, from: data)
        return raw.map { CityOption(id: $0.SehirID, name: $0.SehirAdi) }
    }

    static func getDistricts(cityId: String) async throws -> [DistrictOption] {
        let url = URL(string: "\(baseURL)/ilceler/\(cityId)")!
        let (data, _) = try await session().data(from: url)
        let raw = try JSONDecoder().decode([RawDistrict].self, from: data)
        return raw.map { DistrictOption(id: $0.IlceID, name: $0.IlceAdi) }
    }

    static func getPrayerTimes(districtId: String) async throws -> [PrayerDay] {
        let url = URL(string: "\(baseURL)/vakitler/\(districtId)")!
        let (data, _) = try await session().data(from: url)
        let raw = try JSONDecoder().decode([RawPrayerDay].self, from: data)
        return raw.map {
            PrayerDay(
                tarih: $0.MiladiTarihKisa,
                imsak: $0.Imsak,
                gunes: $0.Gunes,
                ogle: $0.Ogle,
                ikindi: $0.Ikindi,
                aksam: $0.Aksam,
                yatsi: $0.Yatsi
            )
        }
    }
}
