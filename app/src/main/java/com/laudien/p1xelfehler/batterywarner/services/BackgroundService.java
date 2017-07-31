package com.laudien.p1xelfehler.batterywarner.services;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

import com.laudien.p1xelfehler.batterywarner.R;
import com.laudien.p1xelfehler.batterywarner.helper.BatteryHelper;
import com.laudien.p1xelfehler.batterywarner.helper.GraphDbHelper;
import com.laudien.p1xelfehler.batterywarner.helper.NotificationHelper;
import com.laudien.p1xelfehler.batterywarner.helper.RootHelper;
import com.laudien.p1xelfehler.batterywarner.preferences.infoNotificationActivity.InfoNotificationActivity;

import java.util.Locale;

import static android.app.Notification.PRIORITY_HIGH;
import static android.app.Notification.PRIORITY_LOW;
import static android.os.BatteryManager.EXTRA_PLUGGED;
import static android.os.BatteryManager.EXTRA_TEMPERATURE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;
import static android.view.View.GONE;
import static com.laudien.p1xelfehler.batterywarner.helper.NotificationHelper.ID_NOT_ROOTED;
import static com.laudien.p1xelfehler.batterywarner.helper.NotificationHelper.ID_STOP_CHARGING_NOT_WORKING;

public class BackgroundService extends Service {
    public static final int NOTIFICATION_ID_WARNING = 2001;
    private static final int NOTIFICATION_ID_INFO = 2002;
    private boolean chargingPausedBySmartCharging = false;
    private boolean chargingResumedBySmartCharging = false;
    private BatteryChangedReceiver batteryChangedReceiver;
    private SharedPreferences sharedPreferences;
    private RemoteViews infoNotificationContent;
    private String infoNotificationMessage;
    private Object infoNotificationBuilder;
    private NotificationManager notificationManager;
    private BatteryHelper.BatteryData batteryData;
    private int lastBatteryLevel = -1;
    private GraphDbHelper graphDbHelper;
    private boolean alreadyNotified = false;
    private long smartChargingResumeTime;

    public static boolean isChargingTypeEnabled(Context context, int chargingType, @Nullable SharedPreferences sharedPreferences) {
        if (sharedPreferences == null) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        }
        switch (chargingType) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                return sharedPreferences.getBoolean(context.getString(R.string.pref_ac_enabled), context.getResources().getBoolean(R.bool.pref_ac_enabled_default));
            case BatteryManager.BATTERY_PLUGGED_USB:
                return sharedPreferences.getBoolean(context.getString(R.string.pref_usb_enabled), context.getResources().getBoolean(R.bool.pref_usb_enabled_default));
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return sharedPreferences.getBoolean(context.getString(R.string.pref_wireless_enabled), context.getResources().getBoolean(R.bool.pref_wireless_enabled_default));
            default: // discharging or unknown charging type
                return false;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
                if (s.equals(getString(R.string.pref_smart_charging_time_before))
                        || s.equals(getString(R.string.pref_smart_charging_time))
                        || s.equals(getString(R.string.pref_smart_charging_use_alarm_clock_time))) {
                    smartChargingResumeTime = getSmartChargingResumeTime();
                }
            }
        });
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        batteryChangedReceiver = new BatteryChangedReceiver();
        graphDbHelper = GraphDbHelper.getInstance(this);
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        intentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        Intent batteryChangedIntent = registerReceiver(batteryChangedReceiver, intentFilter);
        batteryData = BatteryHelper.getBatteryData(batteryChangedIntent, this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // battery info notification
        boolean infoNotificationEnabled = SDK_INT >= O || sharedPreferences.getBoolean(getString(R.string.pref_info_notification_enabled), getResources().getBoolean(R.bool.pref_info_notification_enabled_default));
        if (infoNotificationEnabled) {
            String[] data = batteryData.getEnabledOnly(this, sharedPreferences);
            infoNotificationContent = buildInfoNotificationContent(data);
            String message = buildInfoNotificationMessage(data);
            Notification infoNotification = buildInfoNotification(infoNotificationContent, message);
            startForeground(NOTIFICATION_ID_INFO, infoNotification);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(batteryChangedReceiver);
    }

    private Notification buildInfoNotification(RemoteViews content, String message) {
        Intent clickIntent = new Intent(this, InfoNotificationActivity.class);
        PendingIntent clickPendingIntent = PendingIntent.getActivity(this, NOTIFICATION_ID_INFO, clickIntent, 0);
        if (SDK_INT >= N) {
            Notification.Builder builder = new Notification.Builder(this)
                    .setOngoing(true)
                    .setContentIntent(clickPendingIntent)
                    .setContentTitle(getString(R.string.title_info_notification))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setCustomBigContentView(content)
                    .setContentText(message);
            if (SDK_INT >= O) {
                builder.setChannelId(getString(R.string.channel_battery_info));
            } else { // Android N (API 24/25)
                builder.setPriority(Notification.PRIORITY_LOW);
            }
            infoNotificationBuilder = builder;
            return builder.build();
        } else { // API lower than N
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setPriority(Notification.PRIORITY_LOW)
                    .setContentIntent(clickPendingIntent)
                    .setContentTitle(getString(R.string.title_info_notification))
                    .setCustomBigContentView(content)
                    .setContentText(message)
                    .setSmallIcon(R.mipmap.ic_launcher);
            infoNotificationBuilder = builder;
            return builder.build();
        }
    }

    private RemoteViews buildInfoNotificationContent(String[] data) {
        if (infoNotificationContent == null) {
            boolean darkThemeEnabled = sharedPreferences.getBoolean(getString(R.string.pref_dark_info_notification), getResources().getBoolean(R.bool.pref_dark_info_notification_default));
            if (darkThemeEnabled) { // dark theme
                infoNotificationContent = new RemoteViews(getPackageName(), R.layout.notification_battery_info_dark);
            } else { // default theme
                infoNotificationContent = new RemoteViews(getPackageName(), R.layout.notification_battery_info);
            }
            if (SDK_INT == LOLLIPOP || SDK_INT == LOLLIPOP_MR1) {
                infoNotificationContent.setImageViewResource(R.id.img_battery, R.mipmap.ic_launcher_round);
            }
        }
        if (data != null && data.length > 0) {
            if (data.length <= 3) {
                infoNotificationContent.setViewVisibility(R.id.textView_message_right, GONE);
                infoNotificationContent.setViewVisibility(R.id.view_middleLine, GONE);
                String message = data[0];
                for (byte i = 1; i < data.length; i++) {
                    message = message.concat("\n").concat(data[i]);
                }
                infoNotificationContent.setTextViewText(R.id.textView_message_left, message);
            } else { // more then 3 items
                String message_left = data[0], message_right = data[1];
                for (byte i = 2; i < data.length; i++) {
                    if (i % 2 == 0) {
                        message_left = message_left.concat("\n").concat(data[i]);
                    } else {
                        message_right = message_right.concat("\n").concat(data[i]);
                    }
                }
                infoNotificationContent.setTextViewText(R.id.textView_message_left, message_left);
                infoNotificationContent.setTextViewText(R.id.textView_message_right, message_right);
            }
        } else {
            infoNotificationContent.setViewVisibility(R.id.view_middleLine, GONE);
            infoNotificationContent.setViewVisibility(R.id.textView_message_right, GONE);
            infoNotificationContent.setTextViewText(R.id.textView_message_left,
                    getString(R.string.notification_no_items_enabled));
        }
        return infoNotificationContent;
    }

    private String buildInfoNotificationMessage(String[] data) {
        if (data != null && data.length > 0) {
            infoNotificationMessage = data[0];
            int i = 1;
            do {
                infoNotificationMessage = infoNotificationMessage.concat(", ").concat(data[i]);
                i++;
            } while (i < data.length);
            return infoNotificationMessage;
        } else {
            return getString(R.string.notification_no_items_enabled);
        }
    }

    private Notification buildStopChargingNotification(boolean enableSound) {
        PendingIntent pendingIntent = PendingIntent.getService(this, NOTIFICATION_ID_WARNING,
                new Intent(this, EnableChargingService.class), PendingIntent.FLAG_CANCEL_CURRENT);
        String messageText = getString(R.string.notification_charging_disabled);
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(messageText)
                .setStyle(NotificationHelper.getBigTextStyle(messageText))
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_battery_charging_full_white_24dp, getString(R.string.notification_button_enable_charging), pendingIntent)
                .setOngoing(true);
        if (SDK_INT >= O) {
            builder.setChannelId(getString(R.string.channel_battery_warnings));
        } else {
            builder.setPriority(PRIORITY_LOW);
            if (enableSound) {
                builder.setSound(NotificationHelper.getWarningSound(this, sharedPreferences, true));
            }
        }
        return builder.build();
    }

    private Notification buildWarningHighNotification() {
        int warningHigh = sharedPreferences.getInt(getString(R.string.pref_warning_high), getResources().getInteger(R.integer.pref_warning_high_default));
        String messageText = String.format(Locale.getDefault(), "%s %d%%!", getString(R.string.notification_warning_high), warningHigh);
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(messageText)
                .setStyle(NotificationHelper.getBigTextStyle(messageText))
                .setContentIntent(NotificationHelper.getDefaultClickIntent(this))
                .setAutoCancel(true)
                .setSound(NotificationHelper.getWarningSound(this, sharedPreferences, true))
                .setVibrate(NotificationHelper.VIBRATE_PATTERN);
        if (Build.VERSION.SDK_INT >= O) {
            builder.setChannelId(getString(R.string.channel_battery_warnings));
        } else {
            builder.setPriority(PRIORITY_HIGH);
        }
        return builder.build();
    }

    private Notification buildWarningLowNotification(int warningLow, boolean showPowerSaving) {
        String messageText = String.format(Locale.getDefault(), "%s %d%%!", getString(R.string.notification_warning_low), warningLow);
        Notification.Builder builder = new Notification.Builder(BackgroundService.this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(messageText)
                .setStyle(NotificationHelper.getBigTextStyle(messageText))
                .setContentIntent(NotificationHelper.getDefaultClickIntent(BackgroundService.this))
                .setAutoCancel(true)
                .setSound(NotificationHelper.getWarningSound(BackgroundService.this, sharedPreferences, false))
                .setVibrate(NotificationHelper.VIBRATE_PATTERN);
        if (Build.VERSION.SDK_INT >= O) {
            builder.setChannelId(getString(R.string.channel_battery_warnings));
        } else {
            builder.setPriority(PRIORITY_HIGH);
        }
        if (showPowerSaving) {
            Intent exitPowerSaveIntent = new Intent(this, TogglePowerSavingService.class);
            PendingIntent pendingIntent = PendingIntent.getService(this, 0, exitPowerSaveIntent, 0);
            builder.addAction(R.drawable.ic_battery_charging_full_white_24dp, getString(R.string.notification_button_toggle_power_saving), pendingIntent);
        }
        return builder.build();
    }

    private void resetBatteryStats() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    RootHelper.resetBatteryStats();
                } catch (RootHelper.NotRootedException e) {
                    e.printStackTrace();
                    NotificationHelper.showNotification(BackgroundService.this, NotificationHelper.ID_NOT_ROOTED);
                }
            }
        });
    }

    private long getSmartChargingResumeTime() {
        long alarmTime; // the target time the device should be charged to the defined maximum
        boolean smartChargingUseClock = sharedPreferences.getBoolean(getString(R.string.pref_smart_charging_use_alarm_clock_time), getResources().getBoolean(R.bool.pref_smart_charging_use_alarm_clock_time_default));
        int smartChargingMinutes = sharedPreferences.getInt(getString(R.string.pref_smart_charging_time_before), getResources().getInteger(R.integer.pref_smart_charging_time_before_default));
        if (SDK_INT >= LOLLIPOP && smartChargingUseClock) { // use alarm clock
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            AlarmManager.AlarmClockInfo alarmClockInfo = alarmManager.getNextAlarmClock();
            if (alarmClockInfo != null) {
                alarmTime = alarmClockInfo.getTriggerTime();
            } else { // no alarm time is set in the alarm app
                NotificationHelper.showNotification(this, NotificationHelper.ID_NO_ALARM_TIME_FOUND);
                sharedPreferences.edit().putBoolean(getString(R.string.pref_smart_charging_use_alarm_clock_time), false).apply();
                return 0;
            }
        } else { // use time in shared preferences (smartChargingTime)
            long smartChargingTime = sharedPreferences.getLong(getString(R.string.pref_smart_charging_time), -1);
            if (smartChargingTime != -1) {
                alarmTime = smartChargingTime;
            } else { // there is no time saved in shared preferences
                return 0;
            }
        }
        long timeBefore = smartChargingMinutes * 60 * 1000;
        long timeNow = System.currentTimeMillis();
        long resumeTime = alarmTime - timeBefore;
        while (resumeTime <= timeNow) {
            alarmTime += 1000 * 60 * 60 * 24; // add a day if time is in the past
            resumeTime = alarmTime - timeBefore;
            // save the new time in the shared preferences
            sharedPreferences.edit().putLong(getString(R.string.pref_smart_charging_time), alarmTime).apply();
        }
        return resumeTime;
    }

    private boolean isWarningSoundEnabled(Intent batteryIntent) {
        boolean warningHighEnabled = sharedPreferences.getBoolean(getString(R.string.pref_warning_high_enabled), getResources().getBoolean(R.bool.pref_warning_high_enabled_default));
        if (!warningHighEnabled) {
            return false;
        } else {
            int chargingType = batteryIntent.getIntExtra(EXTRA_PLUGGED, -1);
            switch (chargingType) {
                case BatteryManager.BATTERY_PLUGGED_AC:
                    return sharedPreferences.getBoolean(getString(R.string.pref_ac_enabled), getResources().getBoolean(R.bool.pref_ac_enabled_default));
                case BatteryManager.BATTERY_PLUGGED_USB:
                    return sharedPreferences.getBoolean(getString(R.string.pref_usb_enabled), getResources().getBoolean(R.bool.pref_usb_enabled_default));
                case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                    return sharedPreferences.getBoolean(getString(R.string.pref_wireless_enabled), getResources().getBoolean(R.bool.pref_wireless_enabled_default));
                default: // discharging or unknown charging type
                    return false;
            }
        }
    }

    private class BatteryChangedReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                boolean isCharging = intent.getIntExtra(EXTRA_PLUGGED, 0) != 0;
                if (isCharging || chargingPausedBySmartCharging) {
                    handleCharging(intent);
                } else { // discharging
                    handleDischarging(intent);
                }
                refreshInfoNotification(intent);
            } else { // started or stopped charging
                lastBatteryLevel = -1;
                alreadyNotified = false;
                if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
                    notificationManager.cancel(NOTIFICATION_ID_WARNING);
                    if (!chargingResumedBySmartCharging) {
                        resetGraph();
                    }
                } else if (!chargingPausedBySmartCharging || chargingResumedBySmartCharging) {
                    notificationManager.cancel(NOTIFICATION_ID_WARNING);
                    resetSmartCharging();
                }
            }
        }

        private void resetGraph() {
            boolean graphEnabled = sharedPreferences.getBoolean(getString(R.string.pref_graph_enabled), getResources().getBoolean(R.bool.pref_graph_enabled_default));
            if (graphEnabled) {
                graphDbHelper.resetTable();
            }
        }

        private void resetSmartCharging() {
            chargingPausedBySmartCharging = false;
            chargingResumedBySmartCharging = false;
        }

        private void handleCharging(Intent intent) {
            long timeNow = System.currentTimeMillis();
            int batteryLevel = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int temperature = intent.getIntExtra(EXTRA_TEMPERATURE, 0);
            boolean smartChargingEnabled = sharedPreferences.getBoolean(getString(R.string.pref_smart_charging_enabled), getResources().getBoolean(R.bool.pref_smart_charging_enabled_default));
            boolean graphEnabled = sharedPreferences.getBoolean(getString(R.string.pref_graph_enabled), getResources().getBoolean(R.bool.pref_graph_enabled_default));
            // battery level changed
            if (batteryLevel != lastBatteryLevel) {
                lastBatteryLevel = batteryLevel;
                // add a value to the database
                if (graphEnabled) {
                    graphDbHelper.addValue(timeNow, batteryLevel, temperature);
                }
                int warningHigh = sharedPreferences.getInt(getString(R.string.pref_warning_high), getResources().getInteger(R.integer.pref_warning_high_default));
                if (batteryLevel >= warningHigh) {
                    if (!chargingResumedBySmartCharging) {
                        boolean stopChargingEnabled = sharedPreferences.getBoolean(getString(R.string.pref_stop_charging), getResources().getBoolean(R.bool.pref_stop_charging_default));
                        if (!alreadyNotified) {
                            alreadyNotified = true;
                            boolean warningEnabled = isWarningSoundEnabled(intent);
                            boolean shouldResetBatteryStats = sharedPreferences.getBoolean(getString(R.string.pref_reset_battery_stats), getResources().getBoolean(R.bool.pref_reset_battery_stats_default));
                            // reset android battery stats
                            if (shouldResetBatteryStats) {
                                resetBatteryStats();
                            }
                            // stop charging
                            if (stopChargingEnabled) {
                                if (smartChargingEnabled) {
                                    chargingPausedBySmartCharging = true;
                                }
                                stopCharging(warningEnabled);
                                // show warning high notification
                            } else if (warningEnabled) {
                                showWarningHighNotification();
                            }
                        }
                    }
                }
            }
            // check smart charging resume time
            if (chargingPausedBySmartCharging) {
                if (!chargingResumedBySmartCharging) {
                    if (smartChargingResumeTime == 0) {
                        smartChargingResumeTime = getSmartChargingResumeTime();
                    }
                    if (timeNow >= smartChargingResumeTime) {
                        // add a graph point for optics/correctness
                        if (graphEnabled) {
                            graphDbHelper.addValue(timeNow, batteryLevel, temperature);
                        }
                        chargingResumedBySmartCharging = true;
                        resumeCharging();
                    }
                } else { // charging already resumed
                    int smartChargingLimit = sharedPreferences.getInt(getString(R.string.pref_smart_charging_limit), getResources().getInteger(R.integer.pref_smart_charging_limit_default));
                    if (batteryLevel >= smartChargingLimit) {
                        stopCharging(false);
                        chargingPausedBySmartCharging = false;
                        chargingResumedBySmartCharging = false;
                    }
                }
            }
        }

        private void handleDischarging(Intent intent) {
            int batteryLevel = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            if (batteryLevel != lastBatteryLevel) {
                lastBatteryLevel = batteryLevel;
                // show warning low notification
                if (!alreadyNotified) {
                    int warningLow = sharedPreferences.getInt(getString(R.string.pref_warning_low), getResources().getInteger(R.integer.pref_warning_low_default));
                    if (batteryLevel <= warningLow) {
                        alreadyNotified = true;
                        showWarningLowNotification(warningLow);
                    }
                }
            }
        }

        private void refreshInfoNotification(Intent intent) {
            batteryData.update(intent, BackgroundService.this);
            if (infoNotificationBuilder != null) {
                String[] data = batteryData.getEnabledOnly(BackgroundService.this, sharedPreferences);
                infoNotificationContent = buildInfoNotificationContent(data);
                infoNotificationMessage = buildInfoNotificationMessage(data);
                //Log.d(getClass().getSimpleName(), "Message: " + infoNotificationMessage);
                Notification notification;
                if (infoNotificationBuilder instanceof Notification.Builder) {
                    Notification.Builder builder = ((Notification.Builder) infoNotificationBuilder)
                            .setContentText(infoNotificationMessage);
                    notification = builder.build();
                } else {
                    NotificationCompat.Builder builder = ((NotificationCompat.Builder) infoNotificationBuilder)
                            .setContentText(infoNotificationMessage);
                    notification = builder.build();
                }
                notificationManager.notify(NOTIFICATION_ID_INFO, notification);
            }
        }

        private void stopCharging(final boolean enableSound) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        RootHelper.disableCharging();
                        Notification stopChargingNotification = buildStopChargingNotification(enableSound);
                        notificationManager.notify(NOTIFICATION_ID_WARNING, stopChargingNotification);
                    } catch (RootHelper.NotRootedException e) {
                        e.printStackTrace();
                        NotificationHelper.showNotification(BackgroundService.this, ID_NOT_ROOTED);
                        showWarningHighNotification();
                    } catch (RootHelper.NoBatteryFileFoundException e) {
                        e.printStackTrace();
                        NotificationHelper.showNotification(BackgroundService.this, ID_STOP_CHARGING_NOT_WORKING);
                        showWarningHighNotification();
                    }
                }
            });
        }

        private void resumeCharging() {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        RootHelper.enableCharging();
                    } catch (RootHelper.NotRootedException e) {
                        e.printStackTrace();
                        NotificationHelper.showNotification(BackgroundService.this, ID_NOT_ROOTED);
                        stopSelf(); // stop service if not rooted!
                    } catch (RootHelper.NoBatteryFileFoundException e) {
                        e.printStackTrace();
                        NotificationHelper.showNotification(BackgroundService.this, ID_STOP_CHARGING_NOT_WORKING);
                    }
                }
            });
        }

        private void showWarningHighNotification() {
            Notification notification = buildWarningHighNotification();
            notificationManager.notify(NOTIFICATION_ID_WARNING, notification);
        }

        private void showWarningLowNotification(final int warningLow) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    boolean showPowerSaving = SDK_INT >= LOLLIPOP && RootHelper.isRootAvailable();
                    Notification notification = buildWarningLowNotification(warningLow, showPowerSaving);
                    notificationManager.notify(NOTIFICATION_ID_WARNING, notification);
                    // enable power saving mode
                    if (showPowerSaving) {
                        boolean enablePowerSaving = sharedPreferences.getBoolean(getString(R.string.pref_power_saving_mode), getResources().getBoolean(R.bool.pref_power_saving_mode_default));
                        if (enablePowerSaving) {
                            try {
                                RootHelper.togglePowerSavingMode(true);
                            } catch (RootHelper.NotRootedException ignored) {
                            } // cannot happen!
                        }
                    }
                }
            });
        }
    }
}