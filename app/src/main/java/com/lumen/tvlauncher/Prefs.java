package com.lumen.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Everything the user customises is stored here. */
final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("lumen", Context.MODE_PRIVATE);
    }

    boolean hasFavorites() {
        return sp.contains("favorites");
    }

    List<String> getFavorites() {
        List<String> out = new ArrayList<>();
        for (String p : sp.getString("favorites", "").split(",")) {
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    void setFavorites(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String p : list) {
            if (b.length() > 0) b.append(',');
            b.append(p);
        }
        sp.edit().putString("favorites", b.toString()).apply();
    }

    Set<String> getHidden() {
        return new HashSet<>(sp.getStringSet("hidden", Collections.<String>emptySet()));
    }

    void setHidden(Set<String> hidden) {
        sp.edit().putStringSet("hidden", new HashSet<>(hidden)).apply();
    }

    int getTheme() { return clamp(sp.getInt("theme", 0), Themes.NAMES.length); }
    void setTheme(int i) { sp.edit().putInt("theme", i).apply(); }

    int getAccent() { return clamp(sp.getInt("accent", 0), Themes.ACCENTS.length); }
    void setAccent(int i) { sp.edit().putInt("accent", i).apply(); }

    int getColumns() { return sp.getInt("columns", 7); }
    void setColumns(int c) { sp.edit().putInt("columns", c).apply(); }

    boolean is24h() { return sp.getBoolean("clock24", false); }
    void set24h(boolean b) { sp.edit().putBoolean("clock24", b).apply(); }

    boolean isOverrideHome() { return sp.getBoolean("overrideHome", true); }
    void setOverrideHome(boolean b) { sp.edit().putBoolean("overrideHome", b).apply(); }

    boolean isOpenOnWake() { return sp.getBoolean("openOnWake", true); }
    void setOpenOnWake(boolean b) { sp.edit().putBoolean("openOnWake", b).apply(); }

    private static int clamp(int i, int size) {
        return (i < 0 || i >= size) ? 0 : i;
    }
}
