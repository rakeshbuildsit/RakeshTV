package com.lumen.tvlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One installed app that can be shown on the home screen. */
final class AppEntry {
    final String pkg;
    final String label;
    final Drawable icon;
    final Drawable banner; // 16:9 TV artwork, null if the app has none
    final Intent launch;

    private AppEntry(String pkg, String label, Drawable icon, Drawable banner, Intent launch) {
        this.pkg = pkg;
        this.label = label;
        this.icon = icon;
        this.banner = banner;
        this.launch = launch;
    }

    /** Loads all launchable apps. TV-optimised apps are preferred. Slow-ish: call off the UI thread. */
    static List<AppEntry> loadAll(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, AppEntry> map = new LinkedHashMap<>();
        collect(pm, map, Intent.CATEGORY_LEANBACK_LAUNCHER, c.getPackageName());
        collect(pm, map, Intent.CATEGORY_LAUNCHER, c.getPackageName());
        addIfLaunchable(pm, map, "com.amazon.venezia"); // Fire TV Appstore isn't always listed

        List<AppEntry> list = new ArrayList<>(map.values());
        Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return list;
    }

    /** Adds an installed app that doesn't show up in the normal app lists but can still be opened. */
    private static void addIfLaunchable(PackageManager pm, Map<String, AppEntry> map, String pkg) {
        if (map.containsKey(pkg)) return;
        try {
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) return;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            Drawable banner = null;
            try {
                banner = ai.loadBanner(pm);
            } catch (Exception ignored) {
            }
            map.put(pkg, new AppEntry(pkg, label == null ? "Appstore" : label.toString(),
                    pm.getApplicationIcon(ai), banner, launch));
        } catch (Exception ignored) {
            // not installed
        }
    }

    private static void collect(PackageManager pm, Map<String, AppEntry> map, String category, String self) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> results;
        try {
            results = pm.queryIntentActivities(query, 0);
        } catch (Exception e) {
            return;
        }
        for (ResolveInfo ri : results) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null) continue;
            String pkg = ai.packageName;
            if (pkg.equals(self) || map.containsKey(pkg)) continue;

            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(new ComponentName(pkg, ai.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            CharSequence label = ri.loadLabel(pm);
            Drawable icon = ri.loadIcon(pm);
            Drawable banner = null;
            try {
                banner = ai.loadBanner(pm);
                if (banner == null && ai.applicationInfo != null) {
                    banner = ai.applicationInfo.loadBanner(pm);
                }
            } catch (Exception ignored) {
            }
            map.put(pkg, new AppEntry(pkg, label == null ? pkg : label.toString(), icon, banner, launch));
        }
    }
}
