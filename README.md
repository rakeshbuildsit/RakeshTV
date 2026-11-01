# RakeshTV – custom home screen for your Redmi Fire TV

Written for the **Redmi Fire TV (TTE_L32R8-FVIN), Fire OS 7.7**. It also works on Android TV and Google TV.

What you get:
- **Opens by itself when the TV turns on**, from standby or a full power-up. You can switch this off in RakeshTV's ⚙ settings.
- **Top left:** your TV's name, a greeting and an **Inputs** button (HDMI / AV)
- **Top right:** time with seconds and the date, plus **Wi-Fi** (shows connected or offline), **Bluetooth** and **⚙** buttons
- **Home apps:** YouTube, Prime Video, Netflix, Hotstar, aha and the Appstore come first, if they're installed. Install any missing ones, then use ⚙ → Reset home apps.
- **All apps grid** with 4–7 apps per row, and no ads or sponsored rows
- 7 wallpaper themes and 6 highlight colours
- Tiles zoom and light up as you move with the remote
- Hold **OK** (or press **☰ Menu**) on any app to add it to favourites, move it left or right, hide it, see app info or uninstall it
- Very lightweight (no extra libraries), so it suits the TV's small memory

> **Honest note about the Home button:** Amazon doesn't let any app replace the Fire TV home screen, and it keeps blocking the tricks people use. RakeshTV works fully as an app. The best everyday setup is to pin it as the **first app** on the Fire TV home (step 4), so it's one click after you press Home.

---

## 1. Build the APK on GitHub (free, nothing to install)
1. Create a free account at github.com and make a **new repository**, for example `lumen-tv`. Set it to **Public** so the TV can download the APK without signing in.
2. On the repository page, choose **Add file → Upload files** and drag in **everything inside this folder**, including the hidden `.github` folder. Then click **Commit**.
   - If `.github` doesn't upload, create the file `.github/workflows/build-apk.yml` with "Add file → Create new file" and paste its contents in.
3. Open the **Actions** tab and wait about 3–5 minutes for a green tick.
4. Your APK is now at this fixed link (put in your own username and repository name):
   ```
   https://github.com/<your-username>/<repo-name>/releases/latest/download/RakeshTVTV.apk
   ```
   Tip: shorten it at tinyurl.com, so on the TV you only type something like `tinyurl.com/mylumen`.

(If you have a PC, you can instead open this folder in Android Studio and choose Build → Build APK(s).)

---

## 2. Prepare the Fire TV (one time)
1. **Unlock Developer options:** go to **Settings → My Fire TV → About**, highlight the first item (your TV's name) and press **OK 7 times**. You'll see "You are now a developer".
2. Press Back. **Developer options** now appears under **My Fire TV**.
3. Install **Downloader** (orange icon, by AFTVnews). Press the search 🔍 on the home screen, type "Downloader", and install it from the Amazon Appstore.
4. Go back to **Settings → My Fire TV → Developer options → Install unknown apps** and turn **Downloader → ON**.

---

## 3. Install RakeshTV over Wi-Fi
1. Open **Downloader** and allow access to files if asked.
2. In the URL box, type your link from step 1, or the tinyurl shortcut.
   - To type faster, install the **Amazon Fire TV** app on your phone, connect to the TV and use your phone keyboard.
3. Press **Go**, then **Install** when the APK opens. Then choose **Done** and **Delete** to remove the leftover file.

**No GitHub?** Install **"Send Files to TV"** on your phone (Play Store) and on the Fire TV (Amazon Appstore). With both on the same Wi-Fi, send the APK from your phone and install it from the TV app. Turn on its "Install unknown apps" permission the same way as in step 2.4.

---

## 4. Put RakeshTV first on your Fire TV home (recommended)
1. Press **Home**, then go to the **Apps** icon (the square grid, "Your Apps & Channels").
2. Highlight **RakeshTV**, press **☰ Menu** on the remote and choose **Move** (or "Move to front").
3. Move it to the very first spot and press OK.

Now Home → OK takes you straight to RakeshTV.

---

## 5. Advanced: try the Home-button takeover (may not work)
RakeshTV includes a small service that switches to RakeshTV whenever the Fire TV home screen opens. Fire TV hides it from the Accessibility menu, so you turn it on with ADB. You can do that from your **phone**, with no PC needed.

> Amazon has blocked these home-button tricks on newer Fire OS versions. One well-known launcher of this type stopped working after a Fire OS update. It may not work on your 7.7.1.7. Trying it is harmless, and steps to undo it are below.

1. On the TV: **Settings → My Fire TV → Developer options → ADB debugging → ON**.
2. Find the TV's IP address in **Settings → My Fire TV → About → Network**.
3. On your Android phone, install an ADB app from the Play Store, such as **"Remote ADB Shell"** or **"Bugjaeger"**. Connect it to `<TV-IP>` on port `5555`. On the TV, accept the "Allow USB debugging?" pop-up and tick "Always allow".
4. Run these two commands:
   ```
   settings put secure enabled_accessibility_services com.lumen.tvlauncher/com.lumen.tvlauncher.HomeOverrideService
   settings put secure accessibility_enabled 1
   ```
   (From a PC, put `adb shell ` in front of each command.)
5. Press Home. If RakeshTV appears, perhaps after a short flash of the Fire home screen, it worked. If Fire home stays, Amazon is blocking it on this version. Use step 4 instead.

**To undo:**
```
settings put secure enabled_accessibility_services ""
```
Or simply uninstall RakeshTV. **Don't** follow reels that *disable* Amazon's launcher with ADB. On Fire TV that can leave you with no working home screen.

---

## 6. Updating later (over Wi-Fi)
Change files on GitHub and it rebuilds automatically. Then on the TV, open **Downloader**, enter the **same link** again, and choose **Install**. Your favourites and settings are kept.

---

## 7. Customising (for tinkerers)
- **Wallpapers and colours:** `app/src/main/java/com/lumen/tvlauncher/Themes.java`
- **Default favourites on first launch:** `DEFAULT_FAVORITES` at the top of `MainActivity.java`
- **Layout, tile sizes and clock:** `build()`, `buildHeader()` and `makeTile()` in `MainActivity.java`
