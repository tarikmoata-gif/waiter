# Waiter Orders (Odoo 17 POS + Epson kitchen printer)

Needs the `kitchen_waiter_api` Odoo module installed (see previous zip).

## Get the APK without installing anything
1. Create a GitHub repo, upload this whole folder (keep `.github/`), commit to `main`.
2. Repo -> Actions -> "Build debug APK" (runs automatically; or Run workflow).
3. Open the finished run -> Artifacts -> `waiter-debug-apk` -> unzip -> `app-debug.apk`.
4. Copy to tablet, allow "Install unknown apps", install.

## Or with Android Studio
Open this folder -> let Gradle sync -> Build > Build APK(s).
Output: app/build/outputs/apk/debug/app-debug.apk

## First run
Settings: Odoo URL, DB name, login (tablet1), API key, printer IP. Save & connect.

## Printer auto-discovery
Settings -> Kitchen printer -> "Scan network" checks TCP port 9100 on your /24 subnet.
One hit is filled in automatically; several hits are listed (use "Test print" to identify yours).
"Auto-find printer" re-scans and reconnects if a print fails (only when exactly one printer answers).
