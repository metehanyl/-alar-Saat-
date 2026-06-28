import SwiftUI

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var pin = ""
    @State private var pinConfirm = ""
    @State private var message: String?
    @State private var messageIsError = true

    var body: some View {
        NavigationStack {
            Form {
                Section("Alarmı kapatma PIN'i") {
                    SecureField("4-6 haneli PIN", text: $pin)
                        .keyboardType(.numberPad)
                    SecureField("PIN'i tekrar girin", text: $pinConfirm)
                        .keyboardType(.numberPad)
                    Button("PIN'i Kaydet") { save() }
                }
                if let message {
                    Section {
                        Text(message).foregroundStyle(messageIsError ? .red : .green)
                    }
                }
            }
            .navigationTitle("Ayarlar")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Kapat") { dismiss() }
                }
            }
        }
    }

    private func save() {
        guard pin.count >= 4 else {
            message = "PIN en az 4 hane olmalı"
            messageIsError = true
            return
        }
        guard pin == pinConfirm else {
            message = "PIN'ler eşleşmiyor"
            messageIsError = true
            return
        }
        PrefsManager.shared.setPin(pin)
        message = "PIN kaydedildi"
        messageIsError = false
    }
}
