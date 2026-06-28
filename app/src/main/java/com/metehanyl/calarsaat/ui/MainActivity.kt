package com.metehanyl.calarsaat.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
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
import com.metehanyl.calarsaat.data.AlarmGroupEntity
import com.metehanyl.calarsaat.data.PinHasher
import com.metehanyl.calarsaat.data.PrefsManager
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
    private var sabahExpanded = false

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
            onToggle = { group, checked -> onGroupToggled(group, checked) },
            onClick = { group ->
                startActivity(
                    Intent(this, GroupEditActivity::class.java)
                        .putExtra(GroupEditActivity.EXTRA_GROUP_ID, group.group.id)
                )
            }
        )
        binding.recyclerGroups.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.recyclerGroups.adapter = groupAdapter
        binding.buttonAddGroup.setOnClickListener { onAddGroupClicked() }

        binding.fabAdd.setOnClickListener { onAddAlarmClicked() }

        binding.switchSabahNamazi.isChecked = prefs.isSabahNamaziEnabled()
        binding.switchSabahNamazi.setOnCheckedChangeListener { _, checked ->
            onSabahNamaziToggled(checked)
        }
        binding.buttonSabahSettings.setOnClickListener { showSabahNamaziSettingsDialog() }
        binding.buttonExpandSabah.setOnClickListener { toggleSabahExpand() }

        requestNotificationPermissionIfNeeded()
        requestBatteryOptimizationExemptionIfNeeded()
        observeAlarms()
        observeGroups()
        observeSabahNamaziAlarms()
    }

    private fun toggleSabahExpand() {
        sabahExpanded = !sabahExpanded
        binding.layoutSabahTimesExpanded.visibility = if (sabahExpanded) View.VISIBLE else View.GONE
        binding.buttonExpandSabah.animate().rotation(if (sabahExpanded) 180f else 0f).start()
    }

    private fun observeSabahNamaziAlarms() {
        lifecycleScope.launch {
            dao.observeAutoSabahNamaziAlarms().collect { alarms ->
                binding.textSabahTimes.text = alarms.joinToString(", ") { "%02d:%02d".format(it.hour, it.minute) }
            }
        }
    }

    private fun showSabahNamaziSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_sabah_namazi_settings, null)
        val editCount = view.findViewById<TextInputEditText>(R.id.editSabahCount)
        val editInterval = view.findViewById<TextInputEditText>(R.id.editSabahInterval)
        val editOffset = view.findViewById<TextInputEditText>(R.id.editSabahOffset)
        val switchPin = view.findViewById<SwitchMaterial>(R.id.switchSabahPin)
        val layoutPin = view.findViewById<TextInputLayout>(R.id.layoutSabahPin)
        val editPin = view.findViewById<TextInputEditText>(R.id.editSabahPin)

        editCount.setText(prefs.getSabahNamaziAlarmCount().toString())
        editInterval.setText(prefs.getSabahNamaziIntervalMinutes().toString())
        editOffset.setText(prefs.getSabahNamaziOffsetMinutes().toString())
        switchPin.isChecked = prefs.isSabahNamaziPinRequired()
        layoutPin.visibility = if (switchPin.isChecked) View.VISIBLE else View.GONE
        switchPin.setOnCheckedChangeListener { _, checked ->
            layoutPin.visibility = if (checked) View.VISIBLE else View.GONE
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.sabah_namazi_settings_title)
            .setView(view)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val requirePin = switchPin.isChecked
                val enteredPin = editPin.text?.toString().orEmpty().trim()
                if (requirePin && enteredPin.isNotEmpty() && enteredPin.length < 4) {
                    Snackbar.make(binding.root, R.string.pin_required_error, Snackbar.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (requirePin && enteredPin.isEmpty() && prefs.getSabahNamaziPinHash() == null) {
                    Snackbar.make(binding.root, R.string.pin_required_error, Snackbar.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val count = editCount.text?.toString()?.toIntOrNull()?.coerceIn(1, 10)
                    ?: PrefsManager.DEFAULT_SABAH_COUNT
                val interval = editInterval.text?.toString()?.toIntOrNull()?.coerceIn(1, 60)
                    ?: PrefsManager.DEFAULT_SABAH_INTERVAL
                val offset = editOffset.text?.toString()?.toIntOrNull()?.coerceIn(0, 120)
                    ?: PrefsManager.DEFAULT_SABAH_OFFSET

                prefs.setSabahNamaziAlarmCount(count)
                prefs.setSabahNamaziIntervalMinutes(interval)
                prefs.setSabahNamaziOffsetMinutes(offset)
                prefs.setSabahNamaziPinRequired(requirePin)
                if (requirePin) {
                    if (enteredPin.isNotEmpty()) prefs.setSabahNamaziPinHash(PinHasher.hash(enteredPin))
                } else {
                    prefs.setSabahNamaziPinHash(null)
                }

                dialog.dismiss()
                if (prefs.isSabahNamaziEnabled()) {
                    performSabahNamaziRefresh()
                }
            }
        }
        dialog.show()
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
                    val timesText = result.times.joinToString(", ") { (h, m) -> "%02d:%02d".format(h, m) }
                    Snackbar.make(
                        binding.root,
                        getString(R.string.sabah_namazi_enabled_message, timesText),
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
        val view = layoutInflater.inflate(R.layout.dialog_group_name, null)
        val editName = view.findViewById<TextInputEditText>(R.id.editDialogGroupName)
        AlertDialog.Builder(this)
            .setTitle(R.string.new_group_title)
            .setView(view)
            .setPositiveButton(R.string.action_create) { _, _ ->
                val name = editName.text?.toString()?.trim().orEmpty()
                if (name.isNotBlank()) {
                    lifecycleScope.launch {
                        val groupId = groupDao.insert(AlarmGroupEntity(name = name)).toInt()
                        startActivity(
                            Intent(this@MainActivity, GroupEditActivity::class.java)
                                .putExtra(GroupEditActivity.EXTRA_GROUP_ID, groupId)
                        )
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun onGroupToggled(group: GroupWithAlarms, enabled: Boolean) {
        lifecycleScope.launch {
            for (alarm in group.alarms) {
                if (enabled) {
                    val next = scheduler.schedule(alarm)
                    dao.update(alarm.copy(enabled = true, nextTriggerAtMillis = next))
                } else {
                    scheduler.cancel(alarm)
                    dao.update(alarm.copy(enabled = false))
                }
            }
        }
    }

    private fun onAddAlarmClicked() {
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
