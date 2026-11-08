# Anime TV (Android TV WebView wrapper)

1. Install Android Studio and open this folder (File > Open). Let Gradle sync.
2. Open app/src/main/java/com/example/animetv/MainActivity.java and change START_URL to your site.
3. Build > Build Bundle(s)/APK(s) > Build APK(s). APK ends up in app/build/outputs/apk/debug/.
4. Install on the TV: copy to a USB drive, or `adb connect <tv-ip>` then `adb install app-debug.apk`.

Remote: D-pad moves a mouse cursor, OK clicks, cursor at the top/bottom screen edge scrolls,
MENU toggles cursor on/off, BACK goes back / exits fullscreen.

## Build without Android Studio (GitHub)
1. Create a free account at github.com and make a new repository.
2. Upload all files from this folder (including the .github folder).
3. Edit MainActivity.java on GitHub (pencil icon) and set START_URL to your site, then commit.
4. Open the Actions tab, click the latest "Build APK" run, wait for the green tick (about 5 minutes).
5. Under Artifacts, download AnimeTV-APK, unzip it, and install app-debug.apk on your TV.
