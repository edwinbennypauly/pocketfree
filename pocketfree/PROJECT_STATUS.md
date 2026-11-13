# PocketFree - Project Status

_Last updated: 2026-11-13 (v0.3.0: investment/withdrawal transaction types, compact tiles, balance follows month; app confirmed working on device)_

## State
All spec features are implemented in code. **Pure logic is compiled and tested here (104 tests pass with Kotlin 2.0.20). The Android/Compose/Room/Hilt layer has NOT been compiled** (sandbox cannot reach Google Maven); it is verified only by syntax check and review. Expect a first CI build that may show compile errors to fix.

## Architecture
- Stack: Kotlin 2.0.20, Compose (BOM 2024.09.00), Material 3, Room 2.6.1 (DB v2), Hilt 2.52, DataStore, Navigation Compose, AndroidX Biometric. AGP 8.5.2, Gradle 8.9, JDK 17. minSdk 26, target 34.
- `domain/` is pure Kotlin/JVM (no Android imports) and fully unit-tested: Money (Long minor units + BigDecimal), RateTable, Calculations, BudgetCalc, Filtering, TransactionValidator, AmountInput (keypad), Recurring, PinHasher, backup/ (Json, Csv, import validation + duplicate planner, BackupJson).
- `data/`: Room entities/DAOs/mappers, SeedCallback, SettingsRepository (DataStore), LedgerRepository (single source for all screens via `snapshot` flow).
- `ui/`: components (scaffold, charts on Canvas, keypad, pickers), screens + ViewModels, nav, lock screen.
- Charts are custom Canvas (no chart library). No INTERNET permission. `allowBackup=false`.

## Decisions
- Four transaction types: EXPENSE, INCOME, INVESTMENT (money moved into investments/savings), WITHDRAWAL (money taken back out). Investment/withdrawal are transfers: never counted as income or expense, excluded from budgets, expense charts, averages and comparisons. Profit on investments is INCOME (default category "Investment returns").
- Total balance = cash = income - expenses - (invested - withdrawn). "Savings" tile = income - expenses (before investing). Analytics adds Savings rate and Net invested.
- Default Investment/Withdrawal categories (8 each) are added to existing installs once via a DataStore flag (no DB schema change, so no migration).
- Small tiles use compact amounts (AED 45.42M) so big numbers never disappear; exact amounts stay on transaction rows and the balance card.
- Home balance card = running balance up to the END of the selected month (changes when you switch months; current month = everything so far). Monthly income/expenses/savings tiles are for that month only.
- Budgets: stored per month, carry forward until changed; limit 0 = cleared; category id 0 = overall budget.
- Categories: renaming/recoloring never touches transactions (FK by id, RESTRICT); delete of a used category archives it.
- Multi-currency: each transaction keeps original amount+currency; totals convert only with user-entered rates (direct or inverse); no rate = excluded and flagged, never guessed.
- Import is a merge: new rows added, exact duplicates skipped by count (so genuine repeat purchases survive), budgets/rates in a JSON backup replace same month/category and same pair. All-or-nothing DB transaction.
- CSV export neutralises spreadsheet formulas (leading ' on text starting with = + - @), import reverses it. CSV is written with a UTF-8 BOM for Excel.
- Recurring: unique (ruleId, date) index + lastGenerated date prevent duplicates; generated on app start.
- PIN: PBKDF2-HMAC-SHA256 (20k iterations, random salt), never stored plain. Locks after 30 s in background. No attempt lockout/backoff yet. **Database is NOT encrypted.**
- Fixed personal signing key committed (`app/pocketfree.jks`) so APK updates install in place. Repo must stay private.
- DB v1 (empty 0.1 foundation) -> v2 uses destructive rebuild only for version 1. Future versions need explicit Migrations.

## Done (spec section -> where)
Dashboard, add/edit/delete/duplicate, history with search/filters/sort, categories (add/rename/icon/color/archive), budgets (overall + per category, warnings at 80%/over), analytics (stats, 4 charts, ranges incl. custom, month comparison), currencies + manual rates, backup/restore (CSV+JSON, SAF, preview, duplicate detection, errors), theme, PIN/biometric/hide amounts, recurring.

## Not built
- Transaction calendar view (optional in spec).
- Separate month-end summary screen (Analytics "This month/Last month" covers it).
- PIN attempt lockout; database encryption (SQLCipher).
- Instrumented (on-device) tests; UI tests. Backup/restore is tested at the logic level (parse/plan/duplicates/round-trip), not through Room.

## Verified vs not
- Verified (run): 104 unit tests incl. mutation check that tests really fail when logic is broken.
- Syntax-checked only: all Android code.
- Unverified: Gradle build, Room schema generation, Hilt graph, Compose rendering, on-device behaviour, R8 release build, biometric prompt, SAF flows.

## Known risks
- First CI run likely needs a fix round.
- Budgets set in one currency and viewed after changing default currency need an exchange rate, otherwise that budget is hidden.
- Release build uses R8; if it misbehaves use the debug APK.

## Files
~65 files; see repo tree. Tests: app/src/test/java/app/pocketfree/domain/**.
