package com.lumen.tvlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Many Mi TVs don't let you pick a different Home app. This service watches
 * for the stock home screen (PatchWall / Android TV home) coming to the front
 * and immediately opens RakeshTV instead, so the remote's Home button "just works".
 * It only looks at the package name of the window that opened.
 */
public class HomeOverrideService extends AccessibilityService {

    private Set<String> stockLaunchers = new HashSet<>();
    private long lastRedirect;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        stockLaunchers = findStockLaunchers(this);
    }

    private long homeDownAt;

    /**
     * Catch the remote's Home button ourselves. If Fire OS handles it, Android blocks other apps
     * from opening for ~5 seconds after a Home press, which made RakeshTV appear late.
     * Short press  -> open RakeshTV instantly.
     * Long press   -> Fire TV's quick settings menu (what a long press normally does).
     * If Fire OS doesn't send us the Home key, the window check below still works as before.
     */
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME || !overrideEnabled()) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) homeDownAt = SystemClock.uptimeMillis();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            boolean longPress = SystemClock.uptimeMillis() - homeDownAt > 600;
            if (longPress) {
                Intent qs = getPackageManager().getLaunchIntentForPackage("com.amazon.tv.quicksettings.ui");
                if (qs == null) qs = getPackageManager().getLeanbackLaunchIntentForPackage("com.amazon.tv.quicksettings.ui");
                if (qs != null) {
                    try { startActivity(qs.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
                }
            } else {
                lastRedirect = SystemClock.uptimeMillis();
                openHome();
            }
            return true;
        }
        return false;
    }

    private void openHome() {
        Intent home = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(home);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !stockLaunchers.contains(pkg.toString())) return;
        if (!overrideEnabled()) return;  // switched off in RakeshTV ⚙ settings

        long now = SystemClock.uptimeMillis();
        if (now - lastRedirect < 1000) return; // avoid loops
        lastRedirect = now;

        openHome();
    }

    @SuppressWarnings("deprecation")
    private boolean overrideEnabled() {
        return getSharedPreferences("lumen", Context.MODE_MULTI_PROCESS).getBoolean("overrideHome", true);
    }

    @Override
    public void onInterrupt() {
    }

    static Set<String> findStockLaunchers(Context c) {
        Set<String> set = new HashSet<>(Arrays.asList(
                "com.amazon.tv.launcher",              // Fire TV home (Redmi Fire TV etc.)
                "com.amazon.firehomestarter",          // Fire TV home starter
                "com.mitv.tvhome",                     // Mi PatchWall
                "com.mitv.tvhome.atv",                 // Mi PatchWall (Android TV builds)
                "com.google.android.tvlauncher",       // Android TV home
                "com.google.android.apps.tv.launcherx" // Google TV home
        ));
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> list = c.getPackageManager()
                    .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo ri : list) {
                String p = ri.activityInfo.packageName;
                // Never hijack Settings / first-boot setup screens
                if (p.contains("settings") || p.contains("provision") || p.contains("setup")) continue;
                set.add(p);
            }
        } catch (Exception ignored) {
        }
        set.remove(c.getPackageName());
        return set;
    }
}
