import Foundation
import SwiftData
import BackgroundTasks

enum SabahNamaziResult {
    case success(times: [(hour: Int, minute: Int)])
    case failure(message: String)
}

/// Mirrors Android's `SabahNamaziManager`. The Android version schedules an
/// exact `AlarmManager` broadcast at 00:05 every night to regenerate the
/// alarms from that day's imsak time. iOS has no exact-background-alarm
/// equivalent: `BGAppRefreshTask` is opportunistic and the system decides the
/// actual fire time, so the daily refresh may run late or be skipped on a
/// given day. To compensate, `MainView` also calls `healIfNeeded()` on every
/// foreground/launch, which is the same self-heal pattern used on the Android
/// side and is the more reliable mechanism here.
enum SabahNamaziManager {
    static let label = "Sabah Namazı"
    static let refreshTaskIdentifier = "com.metehanyl.calarsaat.sabahnamazi.refresh"

    @discardableResult
    static func refresh(context: ModelContext) async -> SabahNamaziResult {
        guard let imsak = await resolveTodaysImsak() else {
            return .failure(message: "İmsak vakti alınamadı.")
        }

        await cancelAutoAlarms(context: context)

        let prefs = PrefsManager.shared
        let count = min(max(prefs.sabahNamaziAlarmCount, 1), 10)
        let interval = min(max(prefs.sabahNamaziIntervalMinutes, 1), 60)
        let offset = min(max(prefs.sabahNamaziOffsetMinutes, 0), 120)
        let requirePin = prefs.isSabahNamaziPinRequired
        let pinHash = prefs.sabahNamaziPinHash

        let base = nextOccurrence(hour: imsak.hour, minute: imsak.minute)
        var createdTimes: [(hour: Int, minute: Int)] = []

        for i in 0..<count {
            let minutesFromBase = offset + i * interval
            guard let triggerDate = Calendar.current.date(byAdding: .minute, value: minutesFromBase, to: base) else { continue }
            let components = Calendar.current.dateComponents([.hour, .minute], from: triggerDate)
            let hour = components.hour ?? imsak.hour
            let minute = components.minute ?? imsak.minute

            let alarm = AlarmEntity(
                hour: hour,
                minute: minute,
                label: label,
                soundId: 0,
                isAutoSabahNamazi: true,
                requirePin: requirePin && pinHash != nil,
                pinHash: pinHash
            )
            context.insert(alarm)
            await AlarmScheduler.schedule(alarm)
            createdTimes.append((hour: hour, minute: minute))
        }
        try? context.save()
        return .success(times: createdTimes)
    }

    static func cancelAutoAlarms(context: ModelContext) async {
        let predicate = #Predicate<AlarmEntity> { $0.isAutoSabahNamazi }
        guard let existing = try? context.fetch(FetchDescriptor(predicate: predicate)) else { return }
        for alarm in existing {
            AlarmScheduler.cancel(alarm)
            context.delete(alarm)
        }
        try? context.save()
    }

    /// Called from `MainView` on launch/foreground: if the toggle is on but
    /// no auto-alarms exist (background refresh never ran, or this is the
    /// first time enabling), regenerate them immediately.
    static func healIfNeeded(context: ModelContext) async {
        guard PrefsManager.shared.isSabahNamaziEnabled else { return }
        let predicate = #Predicate<AlarmEntity> { $0.isAutoSabahNamazi }
        let count = (try? context.fetchCount(FetchDescriptor(predicate: predicate))) ?? 0
        guard count == 0 else { return }
        guard LocationProvider.shared.authorizationStatus == .authorizedWhenInUse
            || LocationProvider.shared.authorizationStatus == .authorizedAlways else { return }
        _ = await refresh(context: context)
    }

    // MARK: Background refresh (best-effort)

    static func scheduleDailyRefreshTask() {
        let request = BGAppRefreshTaskRequest(identifier: refreshTaskIdentifier)
        request.earliestBeginDate = nextOccurrence(hour: 0, minute: 5)
        try? BGTaskScheduler.shared.submit(request)
    }

    static func cancelDailyRefreshTask() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: refreshTaskIdentifier)
    }

    static func handleBackgroundRefresh(task: BGAppRefreshTask, context: ModelContext) {
        scheduleDailyRefreshTask() // always reschedule the next one first
        let operation = Task {
            if PrefsManager.shared.isSabahNamaziEnabled {
                _ = await refresh(context: context)
            }
            task.setTaskCompleted(success: true)
        }
        task.expirationHandler = { operation.cancel() }
    }

    // MARK: Helpers

    private static func nextOccurrence(hour: Int, minute: Int) -> Date {
        let calendar = Calendar.current
        let now = Date()
        var components = calendar.dateComponents([.year, .month, .day], from: now)
        components.hour = hour
        components.minute = minute
        components.second = 0
        guard var candidate = calendar.date(from: components) else { return now }
        if candidate <= now {
            candidate = calendar.date(byAdding: .day, value: 1, to: candidate) ?? candidate
        }
        return candidate
    }

    private static func resolveTodaysImsak() async -> ImsakTime? {
        var bundle = PrayerRepository.getCachedBundle()
        let cachedIsToday = bundle?.todayOrClosest()?.isToday ?? false

        if bundle == nil || !cachedIsToday {
            if case .success(let fresh) = await PrayerRepository.refresh() {
                bundle = fresh
            }
        }

        guard let day = bundle?.todayOrClosest()?.day else { return nil }
        let parts = day.imsak.split(separator: ":")
        guard parts.count == 2, let hour = Int(parts[0]), let minute = Int(parts[1]) else { return nil }
        return ImsakTime(hour: hour, minute: minute)
    }
}
