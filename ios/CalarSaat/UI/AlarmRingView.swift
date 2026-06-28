import SwiftUI

/// Shown full-screen when an alarm notification fires (foreground delivery)
/// or is tapped (background/locked). Mirrors Android's `AlarmRingActivity`:
/// PIN pad if required, swipe-right to dismiss (disabled when PIN is
/// required), swipe-left to snooze 5 minutes. Plays the melody continuously
/// via `AlarmTonePlayer` while this screen is on screen, since the bundled
/// notification sound itself only plays once.
struct AlarmRingView: View {
    let alarm: RingingAlarm
    let onFinished: () -> Void

    @State private var enteredPin = ""
    @State private var showWrongPin = false
    @State private var dragOffset: CGFloat = 0

    private let maxPinLength = 6
    private let swipeThreshold: CGFloat = 120

    var body: some View {
        ZStack {
            Color(red: 0.717, green: 0.110, blue: 0.110).ignoresSafeArea() // ring_background

            VStack(spacing: 24) {
                Spacer()

                Text(currentTimeText())
                    .font(.system(size: 64, weight: .bold))
                    .foregroundStyle(.white)

                if !alarm.label.isEmpty {
                    Text(alarm.label)
                        .font(.title2)
                        .foregroundStyle(.white)
                }

                if !alarm.requirePin {
                    HStack {
                        Text("← Ertele (5 dk)").foregroundStyle(.white.opacity(0.8))
                        Spacer()
                        Text("Kapat →").foregroundStyle(.white.opacity(0.8))
                    }
                    .font(.caption)
                    .padding(.horizontal, 32)
                }

                Spacer()

                if alarm.requirePin {
                    pinSection
                } else {
                    Button("Kapat") { dismissAlarm() }
                        .font(.title2.bold())
                        .foregroundStyle(Color(red: 0.717, green: 0.110, blue: 0.110))
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(.white)
                        .clipShape(Capsule())
                        .padding(.horizontal, 48)
                }

                Spacer()
            }
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture()
                .onChanged { dragOffset = $0.translation.width }
                .onEnded { value in
                    let dx = value.translation.width
                    let dy = value.translation.height
                    defer { dragOffset = 0 }
                    guard abs(dx) > abs(dy), abs(dx) > swipeThreshold else { return }
                    if dx > 0 {
                        if !alarm.requirePin { dismissAlarm() }
                    } else {
                        snoozeAlarm()
                    }
                }
        )
        .onAppear {
            AlarmTonePlayer.shared.start(melody: AlarmSounds.byId(alarm.soundId))
        }
        .onDisappear {
            AlarmTonePlayer.shared.stop()
        }
        .persistentSystemOverlays(.hidden)
    }

    private var pinSection: some View {
        VStack(spacing: 16) {
            Text("Kapatmak için PIN'i girin").foregroundStyle(.white)
            Text(String(repeating: "● ", count: enteredPin.count).trimmingCharacters(in: .whitespaces))
                .font(.title)
                .foregroundStyle(.white)
            if showWrongPin {
                Text("Yanlış PIN").foregroundStyle(.yellow)
            }
            PinPad(onDigit: onDigit, onBackspace: onBackspace)
        }
    }

    private func onDigit(_ digit: String) {
        guard enteredPin.count < maxPinLength else { return }
        enteredPin += digit
        showWrongPin = false
        if enteredPin.count >= 4 {
            if PinHasher.verify(enteredPin, against: alarm.pinHash) {
                dismissAlarm()
                return
            }
            if enteredPin.count == maxPinLength {
                showWrongPin = true
                enteredPin = ""
            }
        }
    }

    private func onBackspace() {
        guard !enteredPin.isEmpty else { return }
        enteredPin.removeLast()
    }

    private func currentTimeText() -> String {
        let now = Date()
        let components = Calendar.current.dateComponents([.hour, .minute], from: now)
        return String(format: "%02d:%02d", components.hour ?? 0, components.minute ?? 0)
    }

    private func dismissAlarm() {
        AlarmTonePlayer.shared.stop()
        onFinished()
    }

    private func snoozeAlarm() {
        AlarmTonePlayer.shared.stop()
        Task {
            await AlarmScheduler.scheduleSnooze(
                alarmId: alarm.id,
                label: alarm.label,
                soundId: alarm.soundId,
                requirePin: alarm.requirePin,
                pinHash: alarm.pinHash
            )
            await MainActor.run { onFinished() }
        }
    }
}

private struct PinPad: View {
    let onDigit: (String) -> Void
    let onBackspace: () -> Void

    private let rows: [[String]] = [
        ["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], ["", "0", "⌫"],
    ]

    var body: some View {
        VStack(spacing: 12) {
            ForEach(rows, id: \.self) { row in
                HStack(spacing: 24) {
                    ForEach(row, id: \.self) { key in
                        Button {
                            if key == "⌫" { onBackspace() } else if !key.isEmpty { onDigit(key) }
                        } label: {
                            Text(key)
                                .font(.title)
                                .frame(width: 64, height: 64)
                                .foregroundStyle(.white)
                        }
                        .opacity(key.isEmpty ? 0 : 1)
                        .disabled(key.isEmpty)
                    }
                }
            }
        }
    }
}
