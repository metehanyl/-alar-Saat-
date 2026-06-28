package com.metehanyl.calarsaat.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.metehanyl.calarsaat.R
import com.metehanyl.calarsaat.alarm.AlarmScheduler
import com.metehanyl.calarsaat.data.AlarmDatabase
import com.metehanyl.calarsaat.data.AlarmEntity
import com.metehanyl.calarsaat.data.PrefsManager
import com.metehanyl.calarsaat.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AlarmAdapter
    private val dao by lazy { AlarmDatabase.getInstance(this).alarmDao() }
    private val scheduler by lazy { AlarmScheduler(this) }
    private val prefs by lazy { PrefsManager(this) }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            } else false
        }

        adapter = AlarmAdapter(
            onToggle = { alarm, checked -> onToggleAlarm(alarm, checked) },
            onClick = { alarm ->
                startActivity(
                    Intent(this, AddEditAlarmActivity::class.java)
                        .putExtra(AddEditAlarmActivity.EXTRA_ALARM_ID, alarm.id)
                )
            }
        )
        binding.recyclerAlarms.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.recyclerAlarms.adapter = adapter

        binding.fabAdd.setOnClickListener { onAddAlarmClicked() }

        requestNotificationPermissionIfNeeded()
        requestBatteryOptimizationExemptionIfNeeded()
        observeAlarms()
    }

    private fun observeAlarms() {
        lifecycleScope.launch {
            dao.observeAll().collect { alarms ->
                adapter.submitList(alarms)
                binding.emptyState.visibility = if (alarms.isEmpty()) android.view.View.VISIBLE
                else android.view.View.GONE
            }
        }
    }

    private fun onAddAlarmClicked() {
        if (!prefs.isPinSet()) {
            showPinRequiredDialog()
            return
        }
        lifecycleScope.launch {
            if (dao.count() >= AlarmDatabase.MAX_ALARMS) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.max_alarms_reached, AlarmDatabase.MAX_ALARMS),
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                startActivity(Intent(this@MainActivity, AddEditAlarmActivity::class.java))
            }
        }
    }

    private fun showPinRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.pin_setup_required_title)
            .setMessage(R.string.pin_setup_required_message)
            .setPositiveButton(R.string.pin_setup_go) { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun onToggleAlarm(alarm: AlarmEntity, enabled: Boolean) {
        lifecycleScope.launch {
            if (enabled) {
                val next = scheduler.schedule(alarm)
                dao.update(alarm.copy(enabled = true, nextTriggerAtMillis = next))
            } else {
                scheduler.cancel(alarm)
                dao.update(alarm.copy(enabled = false))
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestBatteryOptimizationExemptionIfNeeded() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return

        AlertDialog.Builder(this)
            .setTitle(R.string.battery_optimization_title)
            .setMessage(R.string.battery_optimization_message)
            .setPositiveButton(R.string.battery_optimization_action) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
