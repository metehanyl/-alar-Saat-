import Foundation

/// Normalizes Turkish place names for diacritic/case-insensitive matching
/// between sources (CLGeocoder/Nominatim vs. the Diyanet mirror API).
enum TrText {
    private static let replacements: [Character: String] = [
        "Ç": "C", "Ğ": "G", "İ": "I", "Ö": "O", "Ş": "S", "Ü": "U",
        "ç": "C", "ğ": "G", "ı": "I", "ö": "O", "ş": "S", "ü": "U",
    ]

    static func trKey(_ input: String) -> String {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        let upper = trimmed.uppercased(with: Locale(identifier: "tr_TR"))
        var result = ""
        for char in upper {
            result += replacements[char] ?? String(char)
        }
        return result.replacingOccurrences(of: " ", with: "").replacingOccurrences(of: "'", with: "")
    }
}
