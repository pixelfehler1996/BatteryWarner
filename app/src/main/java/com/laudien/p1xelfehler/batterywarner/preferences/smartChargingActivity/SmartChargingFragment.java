package com.laudien.p1xelfehler.batterywarner.preferences.smartChargingActivity;

import android.app.AlarmManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.preference.TwoStatePreference;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.laudien.p1xelfehler.batterywarner.R;
import com.laudien.p1xelfehler.batterywarner.helper.RootHelper;
import com.laudien.p1xelfehler.batterywarner.helper.ToastHelper;
import com.laudien.p1xelfehler.batterywarner.preferences.SeekBarPreference;
import com.laudien.p1xelfehler.batterywarner.receivers.RootCheckFinishedReceiver;

import static android.content.Context.ALARM_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.M;
import static android.widget.Toast.LENGTH_LONG;
import static com.laudien.p1xelfehler.batterywarner.receivers.RootCheckFinishedReceiver.ACTION_ROOT_CHECK_FINISHED;

public class SmartChargingFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final RootCheckFinishedReceiver rootCheckFinishedReceiver = new RootCheckFinishedReceiver() {
        @Override
        protected void disablePreferences(String preferenceKey) {
            if (preferenceKey.equals(getString(R.string.pref_smart_charging_enabled))) {
                ((TwoStatePreference) findPreference(preferenceKey)).setChecked(false);
            }
        }
    };
    private TimePickerPreference timePickerPreference;
    private SwitchPreference alarmTimeSwitch;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.smart_charging);
        setHasOptionsMenu(true);
        if (SDK_INT >= LOLLIPOP) {
            alarmTimeSwitch = (SwitchPreference) findPreference(getString(R.string.pref_smart_charging_use_alarm_clock_time));
        }
        timePickerPreference = (TimePickerPreference) findPreference(getString(R.string.pref_smart_charging_time));
        Context context = getActivity();
        if (context != null) {
            SeekBarPreference seekBar_limit = (SeekBarPreference) findPreference(getString(R.string.pref_smart_charging_limit));
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            int warningHigh = sharedPreferences.getInt(getString(R.string.pref_warning_high), getResources().getInteger(R.integer.pref_warning_high_default));
            seekBar_limit.setMin(warningHigh);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (SDK_INT >= LOLLIPOP) {
            timePickerPreference.setEnabled(!alarmTimeSwitch.isChecked());
        }
        registerObservers();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterObservers();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.smart_charging_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_info) {
            openInfoDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Context context = SDK_INT >= M ? getContext() : getActivity();
        if (context == null) {
            return;
        }
        if (key.equals(getString(R.string.pref_smart_charging_enabled))) {
            final TwoStatePreference preference = (TwoStatePreference) findPreference(key);
            boolean stopChargingEnabled = sharedPreferences.getBoolean(getString(R.string.pref_stop_charging), getResources().getBoolean(R.bool.pref_stop_charging_default));
            if (!stopChargingEnabled) {
                moveBackSwitch(preference, R.string.toast_stop_charging_not_enabled);
            } else {
                RootHelper.handleRootDependingPreference(getActivity(), preference.getKey());
            }
        } else if (SDK_INT >= LOLLIPOP && key.equals(getString(R.string.pref_smart_charging_use_alarm_clock_time))) {
            if (alarmTimeSwitch.isChecked()) { // remove preference key if checked to force the preference to always load the default alarm time
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
                if (alarmManager == null) {
                    moveBackSwitch(alarmTimeSwitch, R.string.toast_error_unknown);
                    return;
                }
                AlarmManager.AlarmClockInfo alarmClockInfo = alarmManager.getNextAlarmClock();
                if (alarmClockInfo != null) {
                    sharedPreferences.edit().remove(timePickerPreference.getKey()).apply();
                } else {
                    moveBackSwitch(alarmTimeSwitch, R.string.toast_no_alarm_time_found);
                }
            } else { // if unchecked, save the time string to shared preferences
                sharedPreferences.edit().putLong(timePickerPreference.getKey(), timePickerPreference.getTime()).apply();
            }
            timePickerPreference.setEnabled(!alarmTimeSwitch.isChecked());
        }
    }

    private void moveBackSwitch(final TwoStatePreference preference, int messageRes) {
        ToastHelper.sendToast(getActivity(), messageRes, LENGTH_LONG);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                preference.setChecked(false);
            }
        }, getResources().getInteger(R.integer.pref_switch_back_delay));
    }

    private void openInfoDialog() {
        Context context = getActivity();
        if (context != null) {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.dialog_title_information)
                    .setView(R.layout.fragment_smart_charging)
                    .setCancelable(true)
                    .setPositiveButton(R.string.dialog_button_close, null)
                    .setIcon(R.drawable.ic_battery_status_full_green_48dp)
                    .create()
                    .show();
        }
    }

    private void registerObservers() {
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        Context context = getActivity();
        if (context != null) {
            context.registerReceiver(rootCheckFinishedReceiver, new IntentFilter(ACTION_ROOT_CHECK_FINISHED));
        }
    }

    private void unregisterObservers() {
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        Context context = getActivity();
        if (context != null) {
            context.unregisterReceiver(rootCheckFinishedReceiver);
        }
    }
}
