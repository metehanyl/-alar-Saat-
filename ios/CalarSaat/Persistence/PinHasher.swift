import Foundation
import CryptoKit

/// SHA-256, unsalted — matches Android's `PinHasher` so a PIN typed on either
/// platform produces the same hash (relevant only if hashes are ever shared/synced).
enum PinHasher {
    static func hash(_ pin: String) -> String {
        let digest = SHA256.hash(data: Data(pin.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    static func verify(_ pin: String, against hash: String?) -> Bool {
        guard let hash else { return false }
        return self.hash(pin) == hash
    }
}
