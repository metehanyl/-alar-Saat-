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
import com.metehanyl.calarsaat.alarm.SabahNamaziManager
import com.metehanyl.calarsaat.alarm.SabahNamaziResult
import com.metehanyl.calarsaat.data.AlarmDatabase
import com.metehanyl.calarsaat.data.AlarmEntity
import com.metehanyl.calarsaat.data.PrefsManager
import com.metehanyl.calarsaat.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AlarmAdapter
    private lateinit var groupAdapter: AlarmGroupAdapter
    private val dao by lazy { AlarmDatabase.getInstance(this).alarmDao() }
    private val groupDao by lazy { AlarmDatabase.getInstance(this).alarmGroupDao() }
    private val scheduler by lazy { AlarmScheduler(this) }
    private val prefs by lazy { PrefsManager(this) }
    private val sabahNamaziManager by lazy { SabahNamaziManager(this) }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            performSabahNamaziRefresh()
        }

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

        groupAdapter = AlarmGroupAdapter(
            onActivate = { group -> onActivateGroupClicked(group) },
            onClick = { group ->
                startActivity(
                    Intent(this, GroupEditActivity::class.java)
                        .putExtra(GroupEditActivity.EXTRA_GROUP_ID, group.group.id)
                )
            }
        )
        binding.recyclerGroups.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )
        binding.recyclerGroups.adapter = groupAdapter
        binding.buttonAddGroup.setOnClickListener { onAddGroupClicked() }

        binding.fabAdd.setOnClickListener { onAddAlarmClicked() }

        binding.switchSabahNamazi.isChecked = prefs.isSabahNamaziEnabled()
        binding.switchSabahNamazi.setOnCheckedChangeListener { _, checked ->
            onSabahNamaziToggled(checked)
        }

        requestNotificationPermissionIfNeeded()
        requestBatteryOptimizationExemptionIfNeeded()
        observeAlarms()
        observeGroups()
    }

    private fun onSabahNamaziToggled(enabled: Boolean) {
        if (enabled) {
            if (hasLocationPermission()) {
                performSabahNamaziRefresh()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        } else {
            prefs.setSabahNamaziEnabled(false)
            lifecycleScope.launch {
                sabahNamaziManager.cancelAutoAlarms()
                sabahNamaziManager.cancelDailyRefresh()
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun performSabahNamaziRefresh() {
        if (!hasLocationPermission()) {
            resetSabahNamaziSwitch()
            return
        }
        lifecycleScope.launch {
            when (val result = sabahNamaziManager.refresh()) {
                is SabahNamaziResult.Success -> {
                    prefs.setSabahNamaziEnabled(true)
                    sabahNamaziManager.scheduleDailyRefresh()
                    val (h1, m1) = result.times[0]
                    val (h2, m2) = result.times[1]
                    val (h3, m3) = result.times[2]
                    Snackbar.make(
                        binding.root,
                        getString(R.string.sabah_namazi_enabled_message, h1, m1, h2, m2, h3, m3),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                is SabahNamaziResult.Failure -> {
                    resetSabahNamaziSwitch()
                    Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun resetSabahNamaziSwitch() {
        binding.switchSabahNamazi.setOnCheckedChangeListener(null)
        binding.switchSabahNamazi.isChecked = false
        binding.switchSabahNamazi.setOnCheckedChangeListener { _, checked ->
            onSabahNamaziToggled(checked)
        }
    }

    private fun observeAlarms() {
        lifecycleScope.launch {
            dao.observeUngrouped().collect { alarms ->
                adapter.submitList(alarms)
                binding.emptyState.visibility = if (alarms.isEmpty()) android.view.View.VISIBLE
                else android.view.View.GONE
            }
        }
    }

    private fun observeGroups() {
        lifecycleScope.launch {
            combine(groupDao.observeAll(), dao.observeAll()) { groups, alarms ->
                groups.map { group -> GroupWithAlarms(group, alarms.filter { it.groupId == group.id }) }
            }.collect { groupAdapter.submitList(it) }
        }
    }

    private fun onAddGroupClicked() {
        if (!prefs.isPinSet()) {
            showPinRequiredDialog()
            return
        }
        startActivity(Intent(this, GroupEditActivity::class.java))
    }

    private fun onActivateGroupClicked(group: GroupWithAlarms) {
        lifecycleScope.launch {
            for (alarm in group.alarms) {
                val next = scheduler.schedule(alarm)
                dao.update(alarm.copy(enabled = true, nextTriggerAtMillis = next))
            }
            Snackbar.make(
                binding.root,
                getString(R.string.group_activated_message, group.group.name),
                Snackbar.LENGTH_SHORT
            ).show()
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
