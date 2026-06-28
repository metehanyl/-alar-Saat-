import SwiftUI
import SwiftData
import CoreLocation

private enum MainSheet: Identifiable {
    case newAlarm
    case editAlarm(AlarmEntity)
    case newGroup
    case editGroup(AlarmGroupEntity)
    case sabahSettings
    case settings

    var id: String {
        switch self {
        case .newAlarm: return "newAlarm"
        case .editAlarm(let a): return "editAlarm-\(a.id)"
        case .newGroup: return "newGroup"
        case .editGroup(let g): return "editGroup-\(g.id)"
        case .sabahSettings: return "sabahSettings"
        case .settings: return "settings"
        }
    }
}

struct MainView: View {
    @Environment(\.modelContext) private var context

    @Query(filter: #Predicate<AlarmEntity> { $0.groupId == nil && !$0.isAutoSabahNamazi },
           sort: [SortDescriptor(\AlarmEntity.hour), SortDescriptor(\AlarmEntity.minute)])
    private var ungroupedAlarms: [AlarmEntity]

    @Query(filter: #Predicate<AlarmEntity> { $0.isAutoSabahNamazi },
           sort: [SortDescriptor(\AlarmEntity.hour), SortDescriptor(\AlarmEntity.minute)])
    private var sabahAlarms: [AlarmEntity]

    @Query(sort: [SortDescriptor(\AlarmGroupEntity.name)])
    private var groups: [AlarmGroupEntity]

    @Query private var allAlarms: [AlarmEntity]

    @State private var activeSheet: MainSheet?
    @State private var sabahExpanded = false
    @State private var sabahEnabled = PrefsManager.shared.isSabahNamaziEnabled
    @State private var locationDeniedMessage: String?

    var body: some View {
        NavigationStack {
            List {
                sabahNamaziSection
                groupsSection
                alarmsSection
            }
            .navigationTitle("Çalar Saat")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { activeSheet = .settings } label: {
                        Image(systemName: "gearshape")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { activeSheet = .newAlarm } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .newAlarm: AddEditAlarmView(existingAlarm: nil)
                case .editAlarm(let alarm): AddEditAlarmView(existingAlarm: alarm)
                case .newGroup: GroupEditView(existingGroup: nil)
                case .editGroup(let group): GroupEditView(existingGroup: group)
                case .sabahSettings: SabahNamaziSettingsView()
                case .settings: SettingsView()
                }
            }
            .alert("Konum izni gerekli", isPresented: .constant(locationDeniedMessage != nil), actions: {
                Button("Tamam") { locationDeniedMessage = nil }
            }, message: {
                Text(locationDeniedMessage ?? "")
            })
            .task {
                await NotificationManager.shared.requestAuthorizationIfNeeded()
                await SabahNamaziManager.healIfNeeded(context: context)
            }
        }
    }

    // MARK: Sabah Namazı

    private var sabahNamaziSection: some View {
        Section {
            HStack {
                VStack(alignment: .leading) {
                    Text("Sabah Namazı").font(.headline)
                    Text("İmsak vaktine göre otomatik 3 alarm kurar")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button { activeSheet = .sabahSettings } label: {
                    Image(systemName: "gearshape")
                }
                .buttonStyle(.plain)
                Toggle("", isOn: $sabahEnabled)
                    .labelsHidden()
                    .onChange(of: sabahEnabled) { _, newValue in toggleSabahNamazi(newValue) }
                Button {
                    withAnimation { sabahExpanded.toggle() }
                } label: {
                    Image(systemName: "chevron.right")
                        .rotationEffect(.degrees(sabahExpanded ? 90 : 0))
                }
                .buttonStyle(.plain)
            }

            if sabahExpanded {
                if sabahAlarms.isEmpty {
                    Text("Henüz alarm kurulmadı. Açmak için yukarıdaki anahtarı kullanın.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(sabahAlarms) { alarm in
                        Text(String(format: "%02d:%02d", alarm.hour, alarm.minute))
                            .font(.subheadline)
                    }
                }
            }
        }
    }

    private func toggleSabahNamazi(_ enabled: Bool) {
        PrefsManager.shared.isSabahNamaziEnabled = enabled
        if enabled {
            let status = LocationProvider.shared.authorizationStatus
            if status == .notDetermined {
                LocationProvider.shared.requestAuthorization()
            } else if status == .denied || status == .restricted {
                locationDeniedMessage = "Sabah namazı alarmları için Ayarlar'dan konum iznini açmanız gerekiyor."
                sabahEnabled = false
                PrefsManager.shared.isSabahNamaziEnabled = false
                return
            }
            Task {
                _ = await SabahNamaziManager.refresh(context: context)
                SabahNamaziManager.scheduleDailyRefreshTask()
            }
        } else {
            SabahNamaziManager.cancelDailyRefreshTask()
            Task { await SabahNamaziManager.cancelAutoAlarms(context: context) }
        }
    }

    // MARK: Groups

    private var groupsSection: some View {
        Section("Alarm Grupları") {
            ForEach(groups) { group in
                GroupRow(
                    group: group,
                    alarms: allAlarms.filter { $0.groupId == group.id },
                    onToggle: { enabled in toggleGroup(group, enabled: enabled) },
                    onTap: { activeSheet = .editGroup(group) }
                )
            }
            Button("+ Yeni Grup") { activeSheet = .newGroup }
        }
    }

    private func toggleGroup(_ group: AlarmGroupEntity, enabled: Bool) {
        let members = allAlarms.filter { $0.groupId == group.id }
        Task {
            for alarm in members {
                alarm.enabled = enabled
                if enabled {
                    alarm.nextTriggerAt = await AlarmScheduler.schedule(alarm)
                } else {
                    AlarmScheduler.cancel(alarm)
                }
            }
            try? context.save()
        }
    }

    // MARK: Ungrouped alarms

    private var alarmsSection: some View {
        Section {
            if ungroupedAlarms.isEmpty {
                VStack(spacing: 4) {
                    Text("Henüz alarm yok").font(.headline)
                    Text("Eklemek için + tuşuna dokunun")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding()
            } else {
                ForEach(ungroupedAlarms) { alarm in
                    AlarmRow(
                        alarm: alarm,
                        onToggle: { enabled in toggleAlarm(alarm, enabled: enabled) },
                        onTap: { activeSheet = .editAlarm(alarm) }
                    )
                }
            }
        }
    }

    private func toggleAlarm(_ alarm: AlarmEntity, enabled: Bool) {
        alarm.enabled = enabled
        Task {
            if enabled {
                alarm.nextTriggerAt = await AlarmScheduler.schedule(alarm)
            } else {
                AlarmScheduler.cancel(alarm)
            }
            try? context.save()
        }
    }
}

private struct AlarmRow: View {
    let alarm: AlarmEntity
    let onToggle: (Bool) -> Void
    let onTap: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(String(format: "%02d:%02d", alarm.hour, alarm.minute))
                    .font(.title2.bold())
                Text(alarm.label.isEmpty ? DayUtils.summarize(alarm.repeatDays) : "\(alarm.label) · \(DayUtils.summarize(alarm.repeatDays))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Toggle("", isOn: Binding(get: { alarm.enabled }, set: onToggle))
                .labelsHidden()
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }
}

private struct GroupRow: View {
    let group: AlarmGroupEntity
    let alarms: [AlarmEntity]
    let onToggle: (Bool) -> Void
    let onTap: () -> Void

    private var anyEnabled: Bool { alarms.contains { $0.enabled } }

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(group.name).font(.headline)
                Text("\(alarms.count) saat")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Toggle("", isOn: Binding(get: { anyEnabled }, set: onToggle))
                .labelsHidden()
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }
}
