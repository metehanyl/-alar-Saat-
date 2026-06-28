import AVFoundation

/// Live sine-wave synthesizer mirroring Android's `AlarmTonePlayer` algorithm
/// (44.1kHz mono, 5ms fade in/out envelope, 0.85 amplitude). Used for in-app
/// melody preview and for continuous looping playback while the ring screen
/// is open in the foreground — a bundled notification sound alone only plays
/// once for ~28s and can't loop indefinitely the way Android's looping
/// AudioTrack can.
final class AlarmTonePlayer {
    static let shared = AlarmTonePlayer()

    private let sampleRate: Double = 44100
    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private var isPlaying = false

    private init() {
        engine.attach(player)
        let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1)!
        engine.connect(player, to: engine.mainMixerNode, format: format)
    }

    func start(melody: AlarmMelody) {
        stop()
        do {
            try configureAudioSession()
            let buffer = renderBuffer(for: melody)
            try engine.start()
            player.scheduleBuffer(buffer, at: nil, options: .loops)
            player.play()
            isPlaying = true
        } catch {
            print("AlarmTonePlayer failed to start: \(error)")
        }
    }

    func stop() {
        guard isPlaying else { return }
        player.stop()
        engine.stop()
        isPlaying = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func configureAudioSession() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .default, options: [.duckOthers])
        try session.setActive(true)
    }

    private func renderBuffer(for melody: AlarmMelody) -> AVAudioPCMBuffer {
        var samples: [Float] = []
        for tone in melody.tones {
            samples.append(contentsOf: synthesizeTone(frequencyHz: tone.frequencyHz, durationMs: tone.durationMs))
        }
        samples.append(contentsOf: [Float](repeating: 0, count: framesFor(ms: melody.tailSilenceMs)))

        let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1)!
        let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(samples.count))!
        buffer.frameLength = AVAudioFrameCount(samples.count)
        let channel = buffer.floatChannelData![0]
        for (i, sample) in samples.enumerated() {
            channel[i] = sample
        }
        return buffer
    }

    private func framesFor(ms: Int) -> Int {
        Int(Double(ms) * sampleRate / 1000)
    }

    private func synthesizeTone(frequencyHz: Int, durationMs: Int) -> [Float] {
        let frames = framesFor(ms: durationMs)
        guard frequencyHz > 0 else { return [Float](repeating: 0, count: frames) }

        let fadeFrames = max(1, Int(0.005 * sampleRate)) // 5ms anti-click envelope
        let angular = 2.0 * Double.pi * Double(frequencyHz) / sampleRate
        var out = [Float](repeating: 0, count: frames)
        for i in 0..<frames {
            let fade: Double
            if i < fadeFrames {
                fade = Double(i) / Double(fadeFrames)
            } else if i >= frames - fadeFrames {
                fade = Double(frames - i) / Double(fadeFrames)
            } else {
                fade = 1.0
            }
            out[i] = Float(sin(angular * Double(i)) * 0.85 * fade)
        }
        return out
    }
}
