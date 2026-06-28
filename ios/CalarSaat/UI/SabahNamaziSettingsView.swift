import SwiftUI
import SwiftData

struct SabahNamaziSettingsView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @State private var count = PrefsManager.shared.sabahNamaziAlarmCount
    @State private var interval = PrefsManager.shared.sabahNamaziIntervalMinutes
    @State private var offset = PrefsManager.shared.sabahNamaziOffsetMinutes
    @State private var requirePin = PrefsManager.shared.isSabahNamaziPinRequired
    @State private var pinText = ""
    @State private var errorMessage: String?
    @State private var isRefreshing = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Stepper("Alarm sayısı: \(count)", value: $count, in: 1...10)
                    Stepper("Alarmlar arası dakika: \(interval)", value: $interval, in: 1...60)
                    Stepper("İmsaktan kaç dakika sonra: \(offset)", value: $offset, in: 0...120)
                }
                Section("PIN") {
                    Toggle("PIN ile kapatma", isOn: $requirePin)
                    if requirePin {
                        SecureField("4-6 haneli PIN", text: $pinText)
                            .keyboardType(.numberPad)
                    }
                }
                if let errorMessage {
                    Section { Text(errorMessage).foregroundStyle(.red) }
                }
            }
            .navigationTitle("Sabah Namazı Ayarları")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { save() }.disabled(isRefreshing)
                }
            }
        }
    }

    private func save() {
        if requirePin && pinText.count < 4 && PrefsManager.shared.sabahNamaziPinHash == nil {
            errorMessage = "PIN en az 4 hane olmalı"
            return
        }
        let prefs = PrefsManager.shared
        prefs.sabahNamaziAlarmCount = count
        prefs.sabahNamaziIntervalMinutes = interval
        prefs.sabahNamaziOffsetMinutes = offset
        prefs.isSabahNamaziPinRequired = requirePin
        if requirePin && pinText.count >= 4 {
            prefs.setSabahNamaziPin(pinText)
        } else if !requirePin {
            prefs.sabahNamaziPinHash = nil
        }

        guard prefs.isSabahNamaziEnabled else {
            dismiss()
            return
        }
        isRefreshing = true
        Task {
            _ = await SabahNamaziManager.refresh(context: context)
            await MainActor.run {
                isRefreshing = false
                dismiss()
            }
        }
    }
}
