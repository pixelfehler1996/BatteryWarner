package com.laudien.p1xelfehler.batterywarner.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

import com.laudien.p1xelfehler.batterywarner.R;
import com.laudien.p1xelfehler.batterywarner.helper.ToastHelper;
import com.laudien.p1xelfehler.batterywarner.receivers.DischargingAlarmReceiver;
import com.laudien.p1xelfehler.batterywarner.services.ChargingService;
import com.laudien.p1xelfehler.batterywarner.services.DischargingService;

import static android.content.Context.MODE_PRIVATE;
import static android.os.BatteryManager.EXTRA_PLUGGED;
import static android.widget.Toast.LENGTH_SHORT;

public class OnOffButtonFragment extends Fragment implements CompoundButton.OnCheckedChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private SharedPreferences sharedPreferences;
    private ToggleButton toggleButton;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        View view = inflater.inflate(R.layout.fragment_on_off_button, container, false);
        toggleButton = (ToggleButton) view.findViewById(R.id.toggleButton);
        boolean isChecked = sharedPreferences.getBoolean(getString(R.string.pref_is_enabled), getResources().getBoolean(R.bool.pref_is_enabled_default));
        toggleButton.setChecked(isChecked);
        toggleButton.setOnCheckedChangeListener(this);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        Context context = getContext();
        Intent batteryStatus = getActivity().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        sharedPreferences.edit().putBoolean(getString(R.string.pref_is_enabled), isChecked).apply();
        if (batteryStatus != null) {
            boolean isCharging = batteryStatus.getIntExtra(EXTRA_PLUGGED, -1) != 0;
            if (isChecked) { // turned on
                SharedPreferences temporaryPrefs = context.getSharedPreferences(getString(R.string.prefs_temporary), MODE_PRIVATE);
                temporaryPrefs.edit().putBoolean(getString(R.string.pref_already_notified), false).apply();
                if (isCharging) {
                    context.startService(new Intent(context, ChargingService.class));
                }
                boolean dischargingServiceEnabled = sharedPreferences.getBoolean(getString(R.string.pref_discharging_service_enabled), getResources().getBoolean(R.bool.pref_discharging_service_enabled_default));
                boolean infoNotificationEnabled = sharedPreferences.getBoolean(getString(R.string.pref_info_notification_enabled), getResources().getBoolean(R.bool.pref_info_notification_enabled_default));
                if (!isCharging && dischargingServiceEnabled || infoNotificationEnabled) { // start DischargingService
                    context.startService(new Intent(context, DischargingService.class));
                } else { // start DischargingAlarmReceiver (if needed)
                    boolean warningLowEnabled = sharedPreferences.getBoolean(context.getString(R.string.pref_warning_low_enabled), context.getResources().getBoolean(R.bool.pref_warning_low_enabled_default));
                    if (warningLowEnabled) {
                        DischargingAlarmReceiver.cancelDischargingAlarm(context);
                        context.sendBroadcast(new Intent(context, DischargingAlarmReceiver.class));
                    }
                }
                ToastHelper.sendToast(context, R.string.toast_successfully_enabled, LENGTH_SHORT);
            } else { // turned off
                if (!isCharging) {
                    DischargingAlarmReceiver.cancelDischargingAlarm(context);
                }
                ToastHelper.sendToast(context, R.string.toast_successfully_disabled, LENGTH_SHORT);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_is_enabled))) {
            toggleButton.setChecked(sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.pref_is_enabled_default)));
        }
    }
}