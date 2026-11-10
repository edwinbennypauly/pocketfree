# PocketFree - Project Status

_Last updated: 2026-11-10 (end of Phase 2)_

## Current phase
Phase 2 (project foundation) written. **Not yet compiled by anyone.** Next step: user pushes to GitHub, runs the workflow, and reports success or pastes errors.

## Decisions
- Stack: Kotlin 2.0.20, Jetpack Compose (BOM 2024.09.00), Material 3, Room 2.6.1, Hilt 2.52, DataStore, Navigation Compose. AGP 8.5.2, Gradle 8.9, JDK 17.
- minSdk 26, target/compile 34. Package `app.pocketfree`. App name PocketFree (placeholder).
- Money = Long minor units; BigDecimal for conversion/format; never Double.
- Budgets: `categoryId == 0` means overall monthly budget (no FK; avoids NULL-in-unique-index duplicates).
- Categories referenced by id with ON DELETE RESTRICT; categories with history are archived, not deleted.
- No INTERNET permission; `allowBackup=false`. Database is NOT encrypted (relies on Android app sandbox + device lock).
- No destructive migration fallback; `exportSchema=true` writes to `app/schemas/`.
- Bottom nav label for Transactions is "History" (fits 5 items); FAB is end-aligned above the bar.
- APK built via GitHub Actions (sandbox cannot reach Google Maven/Gradle hosts, confirmed).
- Charts will be drawn on Compose Canvas (no charting dependency).

## Completed
- Gradle/Hilt/Room/Compose configuration, version catalog, manifest, launcher icon
- Light/dark/system theme (functional, persisted in DataStore)
- Bottom navigation + Add route
- Room schema v1: categories, transactions, budgets, exchange_rates; default categories seeded on first create
- Settings: theme + default currency (functional, persisted)
- Home "foundation check" card (proves DB + settings wiring)
- Money/currency helpers; unit tests for Money and default categories
- GitHub Actions workflow (tests + debug APK)

## Verified so far
- Money parse/format/rounding/overflow logic checked by running an equivalent Java port (all cases pass).
- Kotlin brace/paren balance and XML well-formedness checked.
- **NOT verified:** Kotlin compilation, Hilt/Room annotation processing, Gradle sync, unit tests in Gradle, running on a device.

## Remaining
- Phase 3: dashboard, add/edit/delete, history + search/filter/sort, category management
- Phase 4: budgets, analytics, charts, month comparison
- Phase 5: CSV/JSON export, import with preview + duplicate detection, exchange rates UI, privacy (PIN/biometric/hide amounts)
- Phase 6: full tests and fixes
- Phase 7: signed release APK + instructions
- Extras: recurring transactions, calendar, month-end summary

## Known issues / risks
- First CI run may surface compile errors (code was never compiled). Expect a small fix round.
- Theme flashes SYSTEM for one frame for users who forced a theme (initial StateFlow value).
- `gradle-wrapper.jar` / `gradlew` not included (binary, cannot download here); CI uses setup-gradle instead.

## Files (35)
Root: settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradle/wrapper/gradle-wrapper.properties, .gitignore, README.md, PROJECT_STATUS.md, .github/workflows/build-apk.yml
App: app/build.gradle.kts, app/proguard-rules.pro, AndroidManifest.xml, res (strings, themes x2, icon x3)
Kotlin (app/src/main/java/app/pocketfree): PocketFreeApplication, MainActivity, di/AppModule, domain/{Money,Models}, data/db/{Entities,Daos,DefaultCategories,AppDatabase}, data/repo/SettingsRepository, ui/AppViewModel, ui/theme/{Color,Theme}, ui/nav/AppNav, ui/screens/{HomeScreen,SettingsScreen,PlaceholderScreen}
Tests: domain/MoneyTest, data/DefaultCategoriesTest
