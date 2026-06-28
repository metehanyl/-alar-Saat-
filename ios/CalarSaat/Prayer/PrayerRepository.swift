import Foundation

/// Orchestrates location resolution, reverse geocoding, Diyanet API calls and
/// caching — mirrors Android's `PrayerRepository`.
enum PrayerRepository {
    static func getCachedBundle() -> PrayerBundle? {
        PrayerCache.load()
    }

    static func refresh() async -> RefreshResult {
        switch await resolveLocation() {
        case .failure(let message):
            return .failure(message)
        case .success(let location):
            do {
                let days = try await DiyanetApi.getPrayerTimes(districtId: location.ilceId)
                let bundle = PrayerBundle(
                    sehirId: location.sehirId,
                    sehirAdi: location.sehirAdi,
                    ilceId: location.ilceId,
                    ilceAdi: location.ilceAdi,
                    days: days,
                    fetchedAtEpochMillis: Int64(Date().timeIntervalSince1970 * 1000)
                )
                PrayerCache.save(bundle)
                return .success(bundle)
            } catch {
                return .failure("Namaz vakitleri alınamadı: \(error.localizedDescription)")
            }
        }
    }

    private enum LocationLookup {
        case success(SelectedLocation)
        case failure(String)
    }

    private static func resolveLocation() async -> LocationLookup {
        guard let location = await LocationProvider.shared.getCurrentLocation() else {
            return .failure("Konum alınamadı. Konum izni ve GPS'in açık olduğundan emin olun.")
        }

        let area = await ReverseGeocoder.reverseGeocode(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude)
        guard let il = area.il else {
            return .failure("Konumunuzdan il bilgisi çözümlenemedi.")
        }

        do {
            let cities = try await DiyanetApi.getCities()
            guard let matchedCity = cities.first(where: { TrText.trKey($0.name) == TrText.trKey(il) }) else {
                return .failure("'\(il)' için Diyanet şehir eşleşmesi bulunamadı.")
            }

            let districts = try await DiyanetApi.getDistricts(cityId: matchedCity.id)
            let candidateKeys = area.ilceCandidates.map(TrText.trKey)
            guard let matchedDistrict = districts.first(where: { candidateKeys.contains(TrText.trKey($0.name)) }) else {
                return .failure("'\(il)' içinde ilçe eşleşmesi bulunamadı.")
            }

            return .success(SelectedLocation(
                sehirId: matchedCity.id,
                sehirAdi: matchedCity.name,
                ilceId: matchedDistrict.id,
                ilceAdi: matchedDistrict.name
            ))
        } catch {
            return .failure("Diyanet servisine bağlanılamadı: \(error.localizedDescription)")
        }
    }
}
