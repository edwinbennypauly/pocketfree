# PocketFree

A free, ad-free, offline Android expense tracker. Kotlin + Jetpack Compose + Room. No account, no internet permission.

## Build the APK with GitHub Actions (no PC setup)

1. Create a free **private** repository on github.com.
2. Upload the contents of this folder (the folder's *contents*, so `settings.gradle.kts` sits at the repo root).
   - Easiest: on the empty repo page choose **uploading an existing file**, drag everything in, commit.
   - Make sure the hidden `.github/workflows/build-apk.yml` file was uploaded. If your browser skipped it, create it via **Add file > Create new file** and paste its contents.
3. Open the **Actions** tab. The *Build APK* workflow starts on every push (or press **Run workflow**).
4. When it finishes (about 5-10 min the first time), open the run and download **pocketfree-debug-apk** (and **test-reports**) from *Artifacts*.
5. Unzip it, copy `app-debug.apk` to your phone, open it, and allow "install unknown apps" for your file manager when prompted.

If the run fails, open the failed step, copy the error text, and paste it to me. That is the compile feedback loop.

## Build with Android Studio instead

Open this folder in Android Studio (Koala or newer, JDK 17), let it sync, then **Build > Build APK(s)**. If it complains about the Gradle wrapper, run `gradle wrapper --gradle-version 8.9` once, or let Android Studio use its bundled Gradle.

## Debug vs release

Phase 2 produces a *debug* APK (debug-signed, fine for testing and installable). The signed release APK comes in Phase 7.
