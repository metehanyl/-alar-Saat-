package com.metehanyl.calarsaat.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.metehanyl.calarsaat.AlarmApp
import com.metehanyl.calarsaat.R
import com.metehanyl.calarsaat.ui.AlarmRingActivity

class AlarmRingingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val tonePlayer = AlarmTonePlayer()
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        val label = intent?.getStringExtra(EXTRA_ALARM_LABEL).orEmpty()
        val soundId = intent?.getIntExtra(EXTRA_ALARM_SOUND_ID, 0) ?: 0

        currentAlarmId = alarmId
        currentLabel = label

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification(alarmId, label))
        requestAudioFocus()
        startSound(soundId)
        startVibration()

        return START_STICKY
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        audioManager.requestAudioFocus(request)
        audioFocusRequest = request
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CalarSaat:AlarmWakeLock"
        ).apply { acquire(10 * 60 * 1000L) }
    }

    private fun buildNotification(alarmId: Int, label: String): Notification {
        val fullScreenIntent = Intent(this, AlarmRingActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, label)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, alarmId, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = label.ifBlank { getString(R.string.alarm_default_label) }

        return NotificationCompat.Builder(this, AlarmApp.CHANNEL_ID_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(getString(R.string.notification_alarm_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()
    }

    private fun startSound(soundId: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        try {
            tonePlayer.start(AlarmSounds.byId(soundId))
        } catch (e: Exception) {
            startFallbackRingtone()
        }
    }

    private fun startFallbackRingtone() {
        val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getValidRingtoneUri(this)

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            try {
                setDataSource(this@AlarmRingingService, alarmUri)
                prepare()
                start()
            } catch (e: Exception) {
                release()
            }
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 1000, 500)
        val amplitudes = intArrayOf(0, 255, 0)
        val effect = VibrationEffect.createWaveform(pattern, amplitudes, 0)
        vibrator?.vibrate(effect)
    }

    override fun onDestroy() {
        tonePlayer.stop()
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        audioFocusRequest?.let {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it)
        }
        currentAlarmId = -1
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 42
        var currentAlarmId: Int = -1
        var currentLabel: String = ""

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmRingingService::class.java))
        }
    }
}
