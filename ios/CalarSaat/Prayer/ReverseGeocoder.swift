import CoreLocation
import Foundation

struct GeoArea {
    let il: String?
    let ilceCandidates: [String]
}

/// Converts coordinates to Turkish province/district names, mirroring
/// Android's `ReverseGeocoder`: runs the system geocoder and OpenStreetMap
/// Nominatim in parallel and merges the district candidates from both.
enum ReverseGeocoder {
    private struct NominatimResponse: Decodable {
        struct Address: Decodable {
            let state: String?
            let city_district: String?
            let district: String?
            let county: String?
            let town: String?
            let suburb: String?
            let neighbourhood: String?
        }
        let address: Address?
    }

    static func reverseGeocode(latitude: Double, longitude: Double) async -> GeoArea {
        async let deviceResult = deviceGeocode(latitude: latitude, longitude: longitude)
        async let networkResult = nominatimGeocode(latitude: latitude, longitude: longitude)

        let device = await deviceResult
        let network = await networkResult

        let il = device.il ?? network.il
        var candidates = device.ilceCandidates + network.ilceCandidates
        var seen = Set<String>()
        candidates = candidates.filter { seen.insert($0).inserted }
        return GeoArea(il: il, ilceCandidates: candidates)
    }

    private static func deviceGeocode(latitude: Double, longitude: Double) async -> GeoArea {
        let geocoder = CLGeocoder()
        let location = CLLocation(latitude: latitude, longitude: longitude)
        do {
            let placemarks = try await geocoder.reverseGeocodeLocation(location, preferredLocale: Locale(identifier: "tr_TR"))
            guard let placemark = placemarks.first else { return GeoArea(il: nil, ilceCandidates: []) }
            let candidates = [placemark.subAdministrativeArea, placemark.locality, placemark.subLocality]
                .compactMap { $0 }
            return GeoArea(il: placemark.administrativeArea, ilceCandidates: candidates)
        } catch {
            return GeoArea(il: nil, ilceCandidates: [])
        }
    }

    private static func nominatimGeocode(latitude: Double, longitude: Double) async -> GeoArea {
        var components = URLComponents(string: "https://nominatim.openstreetmap.org/reverse")!
        components.queryItems = [
            URLQueryItem(name: "format", value: "jsonv2"),
            URLQueryItem(name: "lat", value: String(latitude)),
            URLQueryItem(name: "lon", value: String(longitude)),
            URLQueryItem(name: "zoom", value: "14"),
            URLQueryItem(name: "addressdetails", value: "1"),
            URLQueryItem(name: "accept-language", value: "tr"),
        ]
        guard let url = components.url else { return GeoArea(il: nil, ilceCandidates: []) }

        var request = URLRequest(url: url, timeoutInterval: 10)
        request.setValue("CalarSaat-iOS/1.0 (Turkce alarm uygulamasi)", forHTTPHeaderField: "User-Agent")

        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            let response = try JSONDecoder().decode(NominatimResponse.self, from: data)
            guard let address = response.address else { return GeoArea(il: nil, ilceCandidates: []) }
            let candidates = [address.city_district, address.district, address.county, address.town, address.suburb, address.neighbourhood]
                .compactMap { $0 }
            return GeoArea(il: address.state, ilceCandidates: candidates)
        } catch {
            return GeoArea(il: nil, ilceCandidates: [])
        }
    }
}
