package com.lumen.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The home screen: clock, favourites row and a grid of all apps. */
public class MainActivity extends Activity {

    /**
     * Home-screen apps, in order. Each entry: known package names, then (last) a name to
     * match against the app's label, so it still works if a package name differs on this TV.
     */
    private static final String[][] DEFAULT_FAVORITES = {
            {"com.amazon.firetv.youtube", "com.google.android.youtube.tv", "youtube"},
            {"com.amazon.avod", "com.amazon.amazonvideo.livingroom", "prime video"},
            {"com.netflix.ninja", "netflix"},
            {"in.startv.hotstar", "in.startv.hotstar.dplus.tv", "hotstar"},
            {"ahaflix.tv", "com.aha.tv", "aha"},
            {"com.amazon.venezia", "com.android.vending", "appstore"}, // Amazon Appstore / Play Store
    };

    private TextView wifiLabel;
    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            updateNetworkStatus();
            ui.postDelayed(this, 15000);
        }
    };

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout content;
    private TextView greeting;
    private float density;
    private List<AppEntry> apps = new ArrayList<>();
    private String pendingFocusTag;
    private boolean receiverRegistered;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadApps();
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        density = getResources().getDisplayMetrics().density;

        root = new FrameLayout(this);
        setContentView(root);
        applyWallpaper();

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(48), dp(28), dp(48), dp(40));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(packageReceiver, filter);
        }
        receiverRegistered = true;

        WakeService.start(this, false);
        reloadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGreeting();
        ui.removeCallbacks(statusTicker);
        ui.post(statusTicker);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(statusTicker);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already on the home screen: jump back to the top.
        scroll.smoothScrollTo(0, 0);
        focusFirstTile();
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(packageReceiver);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // This is the home screen; there is nothing to go back to.
        scroll.smoothScrollTo(0, 0);
    }

    // ------------------------------------------------------------------ data

    private void reloadApps() {
        final Context appCtx = getApplicationContext();
        new Thread(() -> {
            final List<AppEntry> loaded = AppEntry.loadAll(appCtx);
            ui.post(() -> {
                if (isFinishing()) return;
                apps = loaded;
                seedFavoritesIfFirstRun();
                build();
            });
        }, "lumen-load-apps").start();
    }

    private void seedFavoritesIfFirstRun() {
        if (prefs.hasFavorites()) return;
        prefs.setFavorites(defaultFavorites());
    }

    /** YouTube, Prime Video, Netflix, Hotstar, aha – whichever of them are installed. */
    private List<String> defaultFavorites() {
        Map<String, AppEntry> byPkg = indexApps();
        List<String> favs = new ArrayList<>();
        for (String[] wanted : DEFAULT_FAVORITES) {
            String found = null;
            for (int i = 0; i < wanted.length - 1 && found == null; i++) {
                if (byPkg.containsKey(wanted[i])) found = wanted[i];
            }
            String name = wanted[wanted.length - 1];
            if (found == null) {
                for (AppEntry e : apps) {           // exact name, e.g. "Netflix"
                    if (e.label.trim().equalsIgnoreCase(name)) { found = e.pkg; break; }
                }
            }
            if (found == null) {
                for (AppEntry e : apps) {           // name inside label, e.g. "JioHotstar"
                    String l = e.label.toLowerCase();
                    boolean match = name.length() > 3 ? l.contains(name)
                            : (l.startsWith(name + " ") || l.endsWith(" " + name));
                    if (match) { found = e.pkg; break; }
                }
            }
            if (found != null && !favs.contains(found)) favs.add(found);
        }
        return favs;
    }

    private Map<String, AppEntry> indexApps() {
        Map<String, AppEntry> map = new HashMap<>();
        for (AppEntry e : apps) map.put(e.pkg, e);
        return map;
    }

    // ------------------------------------------------------------------ UI building

    private void applyWallpaper() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, Themes.GRADIENTS[prefs.getTheme()]);
        root.setBackground(g);
    }

    private int accent() {
        return Themes.ACCENTS[prefs.getAccent()];
    }

    private void build() {
        String focusTag = pendingFocusTag;
        pendingFocusTag = null;
        if (focusTag == null) {
            View current = getCurrentFocus();
            if (current != null && current.getTag() instanceof String) focusTag = (String) current.getTag();
        }

        content.removeAllViews();
        content.addView(buildHeader(), matchWrap(0, 0, 0, dp(24)));

        Map<String, AppEntry> byPkg = indexApps();
        Set<String> hidden = prefs.getHidden();

        // ---- Favourites row
        List<AppEntry> favs = new ArrayList<>();
        for (String p : prefs.getFavorites()) {
            AppEntry e = byPkg.get(p);
            if (e != null && !hidden.contains(p)) favs.add(e);
        }
        content.addView(sectionTitle("Favourites"), matchWrap(0, 0, 0, dp(4)));

        if (favs.isEmpty()) {
            TextView hint = text("Hold OK on any app below and choose “Add to favourites”.", 16, 0xB3FFFFFF);
            content.addView(hint, matchWrap(0, dp(8), 0, dp(24)));
        } else {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipChildren(false);
            hs.setClipToPadding(false);
            hs.setPadding(dp(4), dp(8), dp(24), dp(10));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            for (AppEntry e : favs) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(112), dp(63));
                lp.setMargins(0, 0, dp(14), 0);
                row.addView(makeTile(e, "fav:" + e.pkg, true), lp);
            }
            hs.addView(row);
            content.addView(hs, matchWrap(-dp(4), 0, -dp(48), dp(14)));
        }

        // ---- All apps grid
        List<AppEntry> visible = new ArrayList<>();
        for (AppEntry e : apps) if (!hidden.contains(e.pkg)) visible.add(e);
        content.addView(sectionTitle("All apps  ·  " + visible.size()), matchWrap(0, 0, 0, dp(12)));

        int cols = prefs.getColumns();
        int gap = dp(10);
        int avail = getResources().getDisplayMetrics().widthPixels - content.getPaddingLeft() - content.getPaddingRight();
        int tileW = (avail - gap * (cols - 1)) / cols;
        int tileH = tileW * 9 / 16;

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(cols);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        for (int i = 0; i < visible.size(); i++) {
            AppEntry e = visible.get(i);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = tileW;
            lp.height = tileH;
            lp.setMargins(0, 0, (i % cols == cols - 1) ? 0 : gap, gap);
            grid.addView(makeTile(e, "app:" + e.pkg, false), lp);
        }
        content.addView(grid, matchWrap(0, 0, 0, dp(12)));

        TextView tip = text("Tip: hold OK (or press ☰ Menu) on an app to add it to favourites, reorder, hide or uninstall it.", 14, 0x80FFFFFF);
        content.addView(tip, matchWrap(0, dp(8), 0, 0));

        final String target = focusTag;
        ui.post(() -> {
            View v = target == null ? null : content.findViewWithTag(target);
            if (v != null) v.requestFocus();
            else focusFirstTile();
        });
    }

    /**
     * Top bar.
     * Left:  TV name, greeting, [Inputs] button.
     * Right: time with seconds, date, [Wi-Fi] [Bluetooth] [⚙] buttons.
     */
    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP);

        // ---- Left corner
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        TextView tvName = text(deviceName(), 28, Color.WHITE);
        tvName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvName.setSingleLine(true);
        tvName.setEllipsize(TextUtils.TruncateAt.END);
        left.addView(tvName);

        greeting = text("", 17, accent());
        greeting.setPadding(0, dp(2), 0, dp(12));
        left.addView(greeting);
        updateGreeting();

        LinearLayout leftButtons = new LinearLayout(this);
        leftButtons.setOrientation(LinearLayout.HORIZONTAL);
        leftButtons.setClipChildren(false);
        leftButtons.addView(pill("⇄  Inputs", "inputs", v -> showInputs()), pillParams(false));
        left.addView(leftButtons);

        header.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // ---- Right corner
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        right.setClipChildren(false);

        String timeFormat = prefs.is24h() ? "HH:mm:ss" : "h:mm:ss a";
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour(timeFormat);
        clock.setFormat24Hour(timeFormat);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clock.setGravity(Gravity.END);
        right.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM yyyy");
        date.setFormat24Hour("EEEE, d MMMM yyyy");
        date.setTextColor(0xD9FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        date.setGravity(Gravity.END);
        date.setPadding(0, dp(2), 0, dp(12));
        right.addView(date);

        LinearLayout rightButtons = new LinearLayout(this);
        rightButtons.setOrientation(LinearLayout.HORIZONTAL);
        rightButtons.setClipChildren(false);
        wifiLabel = pill("Wi-Fi", "wifi", v -> openSystemScreen(Settings.ACTION_WIFI_SETTINGS, "Network"));
        rightButtons.addView(wifiLabel, pillParams(false));
        rightButtons.addView(pill("Bluetooth", "bluetooth",
                v -> openSystemScreen(Settings.ACTION_BLUETOOTH_SETTINGS, "Controllers & Bluetooth Devices")),
                pillParams(true));
        TextView gear = pill("⚙", "settings", v -> showSettings());
        gear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);   // bigger gear, same tile
        gear.setIncludeFontPadding(false);
        gear.setPadding(dp(12), 0, dp(12), 0);
        rightButtons.addView(gear, pillParams(true));
        right.addView(rightButtons);
        updateNetworkStatus();

        header.addView(right, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    private TextView pill(String label, String tag, View.OnClickListener onClick) {
        TextView t = text(label, 16, Color.WHITE);
        t.setTag(tag);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), 0, dp(18), 0);
        styleFocusable(t);
        t.setOnClickListener(onClick);
        return t;
    }

    private LinearLayout.LayoutParams pillParams(boolean gapBefore) {
        // Same height for every top button, so the larger gear keeps the same tile size
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.setMargins(gapBefore ? dp(12) : 0, 0, 0, 0);
        return lp;
    }

    /** The name set in the TV's settings, e.g. "Rakesh's Fire TV". */
    private String deviceName() {
        String name = null;
        try {
            name = Settings.Global.getString(getContentResolver(), "device_name");
            if (TextUtils.isEmpty(name)) name = Settings.Secure.getString(getContentResolver(), "bluetooth_name");
        } catch (Exception ignored) {
        }
        if (TextUtils.isEmpty(name)) name = Build.MODEL;
        return name;
    }

    @SuppressWarnings("deprecation")
    private void updateNetworkStatus() {
        if (wifiLabel == null) return;
        String state = "Offline";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                state = ni.getType() == ConnectivityManager.TYPE_ETHERNET ? "Ethernet ✓" : "Wi-Fi ✓";
            }
        } catch (Exception ignored) {
        }
        wifiLabel.setText(state.equals("Offline") ? "Wi-Fi · Offline" : state);
    }

    /** Opens a system settings page, falling back to the main TV settings (Fire TV hides some pages). */
    private void openSystemScreen(String action, String fireTvMenuName) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("Opening TV settings – choose “" + fireTvMenuName + "”");
            openTvSettings();
        }
    }

    /** Lists the TV's HDMI / AV inputs and switches to the one you pick. */
    private void showInputs() {
        final List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                for (TvInputInfo info : tim.getTvInputList()) {
                    if (!info.isPassthroughInput()) continue;
                    if (Build.VERSION.SDK_INT >= 24 && info.isHidden(this)) continue;
                    CharSequence label = Build.VERSION.SDK_INT >= 24 ? info.loadCustomLabel(this) : null;
                    if (TextUtils.isEmpty(label)) label = info.loadLabel(this);
                    ids.add(info.getId());
                    names.add(label == null ? info.getId() : label.toString());
                }
            }
        } catch (Exception ignored) {
        }
        if (ids.isEmpty()) {
            toast("Couldn't read the TV's inputs. Opening TV settings – choose “Display & Sounds” or “Inputs”.");
            openTvSettings();
            return;
        }
        dialog().setTitle("Inputs")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    Intent watch = new Intent(Intent.ACTION_VIEW,
                            TvContract.buildChannelUriForPassthroughInput(ids.get(which)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    safeStart(watch, "Couldn't switch to that input");
                })
                .show();
    }

    /** A focusable app tile. Uses the app's TV banner when it has one, else its icon + name. */
    private View makeTile(AppEntry e, String tag, boolean big) {
        FrameLayout tile = new FrameLayout(this);
        tile.setTag(tag);
        tile.setContentDescription(e.label);
        styleFocusable(tile);

        if (e.banner != null) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(e.banner);
            tile.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setPadding(dp(8), dp(6), dp(8), dp(6));
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(e.icon);
            int s = big ? dp(26) : dp(20);
            box.addView(icon, new LinearLayout.LayoutParams(s, s));
            TextView name = text(e.label, big ? 11 : 10, Color.WHITE);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(0, dp(4), 0, 0);
            box.addView(name, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            tile.addView(box, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        tile.setOnClickListener(v -> launch(e));
        tile.setOnLongClickListener(v -> {
            showAppMenu(e);
            return true;
        });
        tile.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
                showAppMenu(e);
                return true;
            }
            return false;
        });
        return tile;
    }

    /** Rounded glass background, accent outline and zoom when focused by the remote. */
    private void styleFocusable(View v) {
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0x26FFFFFF);
        v.setBackground(bg);
        v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        v.setClipToOutline(true);
        v.setFocusable(true);
        v.setClickable(true);
        final int accent = accent();
        v.setOnFocusChangeListener((view, hasFocus) -> {
            bg.setStroke(hasFocus ? dp(3) : 0, accent);
            bg.setColor(hasFocus ? 0x4DFFFFFF : 0x26FFFFFF);
            view.animate()
                    .scaleX(hasFocus ? 1.1f : 1f)
                    .scaleY(hasFocus ? 1.1f : 1f)
                    .translationZ(hasFocus ? dp(14) : 0)
                    .setDuration(140)
                    .start();
        });
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 20, 0xE6FFFFFF);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams matchWrap(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private void updateGreeting() {
        if (greeting == null) return;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon"
                : h < 21 ? "Good evening" : "Good night";
        greeting.setText(g);
    }

    private void focusFirstTile() {
        View v = null;
        List<String> favs = prefs.getFavorites();
        Set<String> hidden = prefs.getHidden();
        for (String p : favs) {
            if (hidden.contains(p)) continue;
            v = content.findViewWithTag("fav:" + p);
            if (v != null) break;
        }
        if (v == null && !apps.isEmpty()) {
            for (AppEntry e : apps) {
                v = content.findViewWithTag("app:" + e.pkg);
                if (v != null) break;
            }
        }
        if (v == null) v = content.findViewWithTag("settings");
        if (v != null) v.requestFocus();
    }

    // ------------------------------------------------------------------ actions

    private void launch(AppEntry e) {
        try {
            startActivity(e.launch);
        } catch (Exception ex) {
            toast("Couldn't open " + e.label);
        }
    }

    private void safeStart(Intent i, String failMessage) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ex) {
            toast(failMessage);
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    private void showAppMenu(final AppEntry e) {
        final List<String> favs = prefs.getFavorites();
        final int idx = favs.indexOf(e.pkg);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (idx < 0) {
            labels.add("★  Add to favourites");
            actions.add(() -> {
                favs.add(e.pkg);
                prefs.setFavorites(favs);
                pendingFocusTag = "fav:" + e.pkg;
                build();
            });
        } else {
            if (idx > 0) {
                labels.add("◀  Move left");
                actions.add(() -> moveFavorite(favs, idx, idx - 1));
            }
            if (idx < favs.size() - 1) {
                labels.add("▶  Move right");
                actions.add(() -> moveFavorite(favs, idx, idx + 1));
            }
            labels.add("☆  Remove from favourites");
            actions.add(() -> {
                favs.remove(e.pkg);
                prefs.setFavorites(favs);
                pendingFocusTag = "app:" + e.pkg;
                build();
            });
        }

        labels.add("Hide from home screen");
        actions.add(() -> {
            Set<String> hidden = prefs.getHidden();
            hidden.add(e.pkg);
            prefs.setHidden(hidden);
            build();
            toast(e.label + " hidden. Unhide it in Settings → Hidden apps.");
        });

        labels.add("App info");
        actions.add(() -> safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + e.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                "App info isn't available on this TV"));

        labels.add("Uninstall");
        actions.add(() -> safeStart(new Intent(Intent.ACTION_DELETE,
                Uri.parse("package:" + e.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                "This app can't be uninstalled from here"));

        dialog().setTitle(e.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void moveFavorite(List<String> favs, int from, int to) {
        String p = favs.remove(from);
        favs.add(to, p);
        prefs.setFavorites(favs);
        pendingFocusTag = "fav:" + p;
        build();
    }

    private void showSettings() {
        final String[] items = {
                "Wallpaper theme  ·  " + Themes.NAMES[prefs.getTheme()],
                "Highlight colour  ·  " + Themes.ACCENT_NAMES[prefs.getAccent()],
                "Apps per row  ·  " + prefs.getColumns(),
                "Clock  ·  " + (prefs.is24h() ? "24-hour" : "12-hour"),
                "Open RakeshTV when TV turns on  ·  " + (prefs.isOpenOnWake() ? "On" : "Off"),
                "Replace Fire TV home (Home / Back go to RakeshTV)  ·  " + (prefs.isOverrideHome() ? "On" : "Off"),
                "Reset home apps (YouTube, Prime, Netflix, Hotstar, aha, Appstore)",
                "Hidden apps",
                "Make RakeshTV my Home screen",
                "TV settings",
                "About",
        };
        dialog().setTitle("RakeshTV settings").setItems(items, (d, which) -> {
            switch (which) {
                case 0: pickTheme(); break;
                case 1: pickAccent(); break;
                case 2: pickColumns(); break;
                case 3:
                    prefs.set24h(!prefs.is24h());
                    pendingFocusTag = "settings";
                    build();
                    break;
                case 4:
                    boolean on = !prefs.isOpenOnWake();
                    prefs.setOpenOnWake(on);
                    if (on) WakeService.start(this, false);
                    else WakeService.stop(this);
                    toast(on ? "RakeshTV will open when the TV turns on" : "RakeshTV won't open on power-on");
                    break;
                case 5:
                    boolean ov = !prefs.isOverrideHome();
                    prefs.setOverrideHome(ov);
                    toast(ov ? "Home and Back from apps will come to RakeshTV"
                             : "Fire TV home is back to normal (until you switch this on again)");
                    break;
                case 6:
                    List<String> defaults = defaultFavorites();
                    prefs.setFavorites(defaults);
                    pendingFocusTag = null;
                    build();
                    toast(defaults.size() < DEFAULT_FAVORITES.length
                            ? "Some of these apps aren't installed yet – install them and reset again"
                            : "Home apps reset");
                    break;
                case 7: manageHidden(); break;
                case 8: showHomeSetup(); break;
                case 9: openTvSettings(); break;
                default:
                    dialog().setTitle("RakeshTV 1.0")
                            .setMessage("A lightweight, customisable home screen for Android TV.")
                            .setPositiveButton("OK", null).show();
            }
        }).show();
    }

    private static boolean isFireTv() {
        return Build.MANUFACTURER != null && Build.MANUFACTURER.equalsIgnoreCase("Amazon")
                || Build.MODEL != null && Build.MODEL.toUpperCase().contains("AFT");
    }

    private void openTvSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (Exception ignored) {
        }
        // Fire TV keeps its settings in its own app
        Intent fire = getPackageManager().getLaunchIntentForPackage("com.amazon.tv.settings.v2");
        if (fire == null) fire = getPackageManager().getLeanbackLaunchIntentForPackage("com.amazon.tv.settings.v2");
        if (fire != null) safeStart(fire.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Couldn't open TV settings");
        else toast("Couldn't open TV settings");
    }

    private void pickTheme() {
        dialog().setTitle("Wallpaper theme")
                .setSingleChoiceItems(Themes.NAMES, prefs.getTheme(), (d, which) -> {
                    prefs.setTheme(which);
                    applyWallpaper();
                    d.dismiss();
                }).show();
    }

    private void pickAccent() {
        dialog().setTitle("Highlight colour")
                .setSingleChoiceItems(Themes.ACCENT_NAMES, prefs.getAccent(), (d, which) -> {
                    prefs.setAccent(which);
                    pendingFocusTag = "settings";
                    build();
                    d.dismiss();
                }).show();
    }

    private void pickColumns() {
        final String[] opts = {"5", "6", "7", "8"};
        int current = Math.max(0, prefs.getColumns() - 5);
        dialog().setTitle("Apps per row")
                .setSingleChoiceItems(opts, Math.min(current, 3), (d, which) -> {
                    prefs.setColumns(which + 5);
                    pendingFocusTag = "settings";
                    build();
                    d.dismiss();
                }).show();
    }

    private void manageHidden() {
        final Set<String> hidden = prefs.getHidden();
        final Map<String, AppEntry> byPkg = indexApps();
        final List<String> pkgs = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String p : hidden) {
            AppEntry e = byPkg.get(p);
            if (e != null) {
                pkgs.add(p);
                names.add(e.label);
            }
        }
        if (pkgs.isEmpty()) {
            toast("No hidden apps");
            return;
        }
        final boolean[] stillHidden = new boolean[pkgs.size()];
        for (int i = 0; i < stillHidden.length; i++) stillHidden[i] = true;
        dialog().setTitle("Hidden apps (untick to show again)")
                .setMultiChoiceItems(names.toArray(new String[0]), stillHidden,
                        (d, which, checked) -> stillHidden[which] = checked)
                .setPositiveButton("Save", (d, w) -> {
                    for (int i = 0; i < pkgs.size(); i++) {
                        if (!stillHidden[i]) hidden.remove(pkgs.get(i));
                    }
                    prefs.setHidden(hidden);
                    pendingFocusTag = "settings";
                    build();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHomeSetup() {
        if (isFireTv()) {
            String fireMsg = "Fire TV doesn't let apps replace its home screen, and Amazon blocks most workarounds.\n\n"
                    + "Best option: on the Fire TV home, open Your Apps, highlight RakeshTV, press ☰ Menu → Move, "
                    + "and put it first. Then RakeshTV is one click away after you press Home.\n\n"
                    + "Advanced: you can try the Home-button service using ADB from your phone "
                    + "(see the README). It may not work on newer Fire OS versions.";
            dialog().setTitle("RakeshTV on Fire TV")
                    .setMessage(fireMsg)
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        String msg = "To make the remote's Home button open RakeshTV:\n\n"
                + "1. Choose “Open accessibility settings”, find “RakeshTV – Home button” and switch it on.\n\n"
                + "2. If your TV has a “Home app” setting, “Choose Home app” lets you pick RakeshTV directly.\n\n"
                + "If neither option exists on your Mi TV, use the ADB commands in the README. "
                + "To go back to PatchWall at any time, switch the service off or uninstall RakeshTV.";
        dialog().setTitle("Make RakeshTV your Home screen")
                .setMessage(msg)
                .setPositiveButton("Open accessibility settings", (d, w) ->
                        safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                                "Accessibility settings aren't available on this TV – see the README for the ADB method"))
                .setNeutralButton("Choose Home app", (d, w) ->
                        safeStart(new Intent(Settings.ACTION_HOME_SETTINGS),
                                "This TV doesn't have a Home app setting – use the accessibility option instead"))
                .setNegativeButton("Close", null)
                .show();
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
