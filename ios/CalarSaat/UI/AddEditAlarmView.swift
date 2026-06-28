import SwiftUI
import SwiftData

private let maxAlarms = 20

struct AddEditAlarmView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    let existingAlarm: AlarmEntity?

    @State private var time = Date()
    @State private var label = ""
    @State private var selectedDays: Set<Int> = []
    @State private var soundId = 0
    @State private var requirePin = false
    @State private var pinText = ""
    @State private var isPreviewing = false
    @State private var showDeleteConfirm = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker("Saat", selection: $time, displayedComponents: .hourAndMinute)
                        .datePickerStyle(.wheel)
                        .environment(\.locale, Locale(identifier: "tr_TR")) // forces 24-hour display
                }

                Section("Etiket") {
                    TextField("Etiket (örn. İş)", text: $label)
                }

                Section("Tekrar") {
                    DayChipsRow(selectedDays: $selectedDays)
                    Text(DayUtils.summarize(selectedDays))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("Alarm Melodisi") {
                    ForEach(AlarmSounds.all) { melody in
                        HStack {
                            Text(AlarmSounds.displayName(melody.nameKey))
                            Spacer()
                            if soundId == melody.id {
                                Image(systemName: "checkmark").foregroundStyle(.tint)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { soundId = melody.id }
                    }
                    Button(isPreviewing ? "Durdur" : "Dinle") { togglePreview() }
                }

                Section("PIN") {
                    Toggle("PIN ile kapatma", isOn: $requirePin)
                    if requirePin {
                        SecureField("4-6 haneli PIN", text: $pinText)
                            .keyboardType(.numberPad)
                    }
                }

                if existingAlarm != nil {
                    Section {
                        Button("Sil", role: .destructive) { showDeleteConfirm = true }
                    }
                }

                if let errorMessage {
                    Section { Text(errorMessage).foregroundStyle(.red) }
                }
            }
            .navigationTitle(existingAlarm == nil ? "Yeni Alarm" : "Alarmı Düzenle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { save() }
                }
            }
            .alert("Bu alarmı silmek istiyor musunuz?", isPresented: $showDeleteConfirm) {
                Button("Sil", role: .destructive) { delete() }
                Button("Vazgeç", role: .cancel) {}
            }
            .onAppear(perform: loadExisting)
            .onDisappear { AlarmTonePlayer.shared.stop() }
        }
    }

    private func loadExisting() {
        guard let alarm = existingAlarm else { return }
        var components = DateComponents()
        components.hour = alarm.hour
        components.minute = alarm.minute
        time = Calendar.current.date(from: components) ?? Date()
        label = alarm.label
        selectedDays = alarm.repeatDays
        soundId = alarm.soundId
        requirePin = alarm.requirePin
    }

    private func togglePreview() {
        if isPreviewing {
            AlarmTonePlayer.shared.stop()
        } else {
            AlarmTonePlayer.shared.start(melody: AlarmSounds.byId(soundId))
        }
        isPreviewing.toggle()
    }

    private func save() {
        if requirePin && pinText.count < 4 && existingAlarm?.pinHash == nil {
            errorMessage = "PIN en az 4 hane olmalı"
            return
        }

        let components = Calendar.current.dateComponents([.hour, .minute], from: time)
        let hour = components.hour ?? 0
        let minute = components.minute ?? 0

        let alarm: AlarmEntity
        if let existing = existingAlarm {
            alarm = existing
        } else {
            let total = (try? context.fetchCount(FetchDescriptor<AlarmEntity>())) ?? 0
            if total >= maxAlarms {
                errorMessage = "En fazla \(maxAlarms) alarm kurabilirsiniz"
                return
            }
            alarm = AlarmEntity(hour: hour, minute: minute, label: label)
            context.insert(alarm)
        }

        alarm.hour = hour
        alarm.minute = minute
        alarm.label = label
        alarm.repeatDays = selectedDays
        alarm.soundId = soundId
        alarm.requirePin = requirePin
        if requirePin {
            if pinText.count >= 4 {
                alarm.pinHash = PinHasher.hash(pinText)
            }
            // else: keep previously stored hash unchanged
        } else {
            alarm.pinHash = nil
        }
        if existingAlarm == nil { alarm.enabled = true }

        try? context.save()
        AlarmTonePlayer.shared.stop()
        Task {
            let next = await AlarmScheduler.schedule(alarm)
            alarm.nextTriggerAt = next
            try? context.save()
            await MainActor.run { dismiss() }
        }
    }

    private func delete() {
        guard let alarm = existingAlarm else { return }
        AlarmScheduler.cancel(alarm)
        context.delete(alarm)
        try? context.save()
        dismiss()
    }
}

struct DayChipsRow: View {
    @Binding var selectedDays: Set<Int>

    var body: some View {
        HStack(spacing: 8) {
            ForEach(DayUtils.orderedDays, id: \.0) { day, name in
                let isSelected = selectedDays.contains(day)
                Text(name)
                    .font(.caption)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(isSelected ? Color.accentColor : Color(.systemGray5))
                    .foregroundStyle(isSelected ? Color.white : Color.primary)
                    .clipShape(Capsule())
                    .onTapGesture {
                        if isSelected { selectedDays.remove(day) } else { selectedDays.insert(day) }
                    }
            }
        }
    }
}
