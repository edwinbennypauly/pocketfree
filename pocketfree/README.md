# PocketFree 0.2

A free, ad-free, offline Android expense tracker. Kotlin + Jetpack Compose + Room. No account, no internet permission.

## Update your GitHub repo with this version

1. Open your repo, go into the `pocketfree/` folder, **Add file > Upload files**, and drag in the *contents* of this folder (keep the folder structure). Same-named files are replaced.
2. Optional tidy-up, delete these three old files (they still compile, they are just unused):
   - `app/src/main/java/app/pocketfree/data/db/DefaultCategories.kt`
   - `app/src/test/java/app/pocketfree/data/DefaultCategoriesTest.kt`
   - `app/src/main/java/app/pocketfree/ui/screens/PlaceholderScreen.kt`
3. Edit your **root** `.github/workflows/build-apk.yml` (the one that has `working-directory: pocketfree`) so the build and upload steps match `.github/workflows/build-apk.yml` in this folder: build with `gradle :app:assembleDebug :app:assembleRelease`, and upload both `pocketfree/app/build/outputs/apk/debug/*.apk` and `.../release/*.apk`.
4. Commit. Actions starts a build. Download **pocketfree-apks** from the run's Artifacts.

Keep the repository **private**: it contains the personal signing key (`app/pocketfree.jks`) so that every build is signed the same way.

## Installing and updating on your phone

- First time: install `app-debug.apk` (recommended to start with) or `app-release.apk` (smaller, optimised; if it ever crashes, use the debug one).
- Updating: install the new APK over the old one. Because every build uses the same signing key, Android updates in place and **your data is kept**.
- If you installed the earlier test build (0.1, signed with a throwaway key), Android will refuse to update it. Uninstall that one first. (It held no data.)
- Before any update, make a backup: Settings > Backup & restore > Full backup (JSON).
- The database upgrade from the 0.1 test build rebuilds an empty database (0.1 had no way to store data). From 0.2 onward upgrades use real migrations.

## Build locally instead

Open the folder in Android Studio (JDK 17), then Build > Build APK(s).
