package com.lumen.tvlauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Keeps a tiny listener running so RakeshTV opens when the TV is switched on
 * (both from a full power-up and from standby, which is how most TVs "turn on").
 */
public class WakeService extends Service {

    static final String EXTRA_FROM_BOOT = "from_boot";
    private static final String CHANNEL = "lumen_wake";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean registered;

    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Give the stock home screen a moment to appear, then come up on top of it.
            openRakeshTVAfter(1500);
        }
    };

    static void start(Context c, boolean fromBoot) {
        if (!new Prefs(c).isOpenOnWake()) return;
        Intent i = new Intent(c, WakeService.class).putExtra(EXTRA_FROM_BOOT, fromBoot);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) {
        }
    }

    static void stop(Context c) {
        try {
            c.stopService(new Intent(c, WakeService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL, "Open RakeshTV on power-on", NotificationManager.IMPORTANCE_MIN));
            }
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Notification n = b.setSmallIcon(getApplicationInfo().icon)
                .setContentTitle("RakeshTV")
                .setContentText("Opens RakeshTV when the TV turns on")
                .setOngoing(true)
                .build();
        try {
            startForeground(1, n);
        } catch (Exception ignored) {
        }

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_DREAMING_STOPPED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(wakeReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(wakeReceiver, f);
        }
        registered = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra(EXTRA_FROM_BOOT, false)) {
            // After a full power-up the stock home screen loads slowly; wait a bit longer.
            openRakeshTVAfter(4000);
        }
        return START_STICKY;
    }

    private void openRakeshTVAfter(long delayMs) {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            if (!openOnWakeFresh()) return;
            Intent home = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            try {
                startActivity(home);
            } catch (Exception ignored) {
            }
        }, delayMs);
    }

    /**
     * This service runs in its own small process (":wake") so the home screen's memory can be
     * freed while you watch YouTube etc. Read the setting fresh, since the main app may change it.
     */
    @SuppressWarnings("deprecation")
    private boolean openOnWakeFresh() {
        return getSharedPreferences("lumen", Context.MODE_MULTI_PROCESS).getBoolean("openOnWake", true);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (registered) {
            try {
                unregisterReceiver(wakeReceiver);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
