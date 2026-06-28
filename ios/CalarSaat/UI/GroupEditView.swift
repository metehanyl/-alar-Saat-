import SwiftUI
import SwiftData

private let maxAlarms = 20

private struct GroupTime: Identifiable {
    let id = UUID()
    var date: Date
}

struct GroupEditView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    let existingGroup: AlarmGroupEntity?

    @State private var name = ""
    @State private var times: [GroupTime] = []
    @State private var newTime = Date()
    @State private var soundId = 0
    @State private var requirePin = false
    @State private var pinText = ""
    @State private var isPreviewing = false
    @State private var showDeleteConfirm = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Grup Adı") {
                    TextField("Grup adı (örn. İş Günleri)", text: $name)
                }

                Section("Saatler") {
                    if times.isEmpty {
                        Text("Henüz saat eklenmedi").foregroundStyle(.secondary)
                    }
                    ForEach(times) { time in
                        Text(time.date.formatted(date: .omitted, time: .shortened))
                    }
                    .onDelete { indices in times.remove(atOffsets: indices) }

                    DatePicker("Yeni saat", selection: $newTime, displayedComponents: .hourAndMinute)
                        .environment(\.locale, Locale(identifier: "tr_TR"))
                    Button("+ Saat Ekle") { times.append(GroupTime(date: newTime)) }
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

                if existingGroup != nil {
                    Section {
                        Button("Grubu Sil", role: .destructive) { showDeleteConfirm = true }
                    }
                }

                if let errorMessage {
                    Section { Text(errorMessage).foregroundStyle(.red) }
                }
            }
            .navigationTitle(existingGroup == nil ? "Yeni Grup" : "Grubu Düzenle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { save() }
                }
            }
            .alert("Bu grubu ve içindeki tüm alarmları silmek istiyor musunuz?", isPresented: $showDeleteConfirm) {
                Button("Sil", role: .destructive) { delete() }
                Button("Vazgeç", role: .cancel) {}
            }
            .onAppear(perform: loadExisting)
            .onDisappear { AlarmTonePlayer.shared.stop() }
        }
    }

    private func loadExisting() {
        guard let group = existingGroup else { return }
        name = group.name
        let groupId = group.id
        let predicate = #Predicate<AlarmEntity> { $0.groupId == groupId }
        let alarms = (try? context.fetch(FetchDescriptor(predicate: predicate))) ?? []
        times = alarms.map { alarm in
            var components = DateComponents()
            components.hour = alarm.hour
            components.minute = alarm.minute
            return GroupTime(date: Calendar.current.date(from: components) ?? Date())
        }
        if let first = alarms.first {
            soundId = first.soundId
            requirePin = first.requirePin
        }
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
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            errorMessage = "Grup adı gerekli"
            return
        }
        guard !times.isEmpty else {
            errorMessage = "En az bir saat ekleyin"
            return
        }
        if requirePin && pinText.count < 4 && !hasExistingPinHash() {
            errorMessage = "PIN en az 4 hane olmalı"
            return
        }

        let group: AlarmGroupEntity
        if let existing = existingGroup {
            group = existing
        } else {
            let total = (try? context.fetchCount(FetchDescriptor<AlarmEntity>())) ?? 0
            if total + times.count > maxAlarms {
                errorMessage = "En fazla \(maxAlarms) alarm kurabilirsiniz"
                return
            }
            group = AlarmGroupEntity(name: trimmedName)
            context.insert(group)
        }
        group.name = trimmedName

        let groupId = group.id
        let predicate = #Predicate<AlarmEntity> { $0.groupId == groupId }
        let oldAlarms = (try? context.fetch(FetchDescriptor(predicate: predicate))) ?? []
        let pinHash: String? = requirePin ? (pinText.count >= 4 ? PinHasher.hash(pinText) : oldAlarms.first?.pinHash) : nil
        for old in oldAlarms {
            AlarmScheduler.cancel(old)
            context.delete(old)
        }

        var newAlarms: [AlarmEntity] = []
        for time in times {
            let components = Calendar.current.dateComponents([.hour, .minute], from: time.date)
            let alarm = AlarmEntity(
                hour: components.hour ?? 0,
                minute: components.minute ?? 0,
                label: trimmedName,
                groupId: groupId,
                requirePin: requirePin,
                pinHash: pinHash
            )
            context.insert(alarm)
            newAlarms.append(alarm)
        }
        try? context.save()
        AlarmTonePlayer.shared.stop()

        Task {
            for alarm in newAlarms {
                let next = await AlarmScheduler.schedule(alarm)
                alarm.nextTriggerAt = next
            }
            try? context.save()
            await MainActor.run { dismiss() }
        }
    }

    private func hasExistingPinHash() -> Bool {
        guard let group = existingGroup else { return false }
        let groupId = group.id
        let predicate = #Predicate<AlarmEntity> { $0.groupId == groupId }
        let alarms = (try? context.fetch(FetchDescriptor(predicate: predicate))) ?? []
        return alarms.first?.pinHash != nil
    }

    private func delete() {
        guard let group = existingGroup else { return }
        let groupId = group.id
        let predicate = #Predicate<AlarmEntity> { $0.groupId == groupId }
        let alarms = (try? context.fetch(FetchDescriptor(predicate: predicate))) ?? []
        for alarm in alarms {
            AlarmScheduler.cancel(alarm)
            context.delete(alarm)
        }
        context.delete(group)
        try? context.save()
        dismiss()
    }
}
