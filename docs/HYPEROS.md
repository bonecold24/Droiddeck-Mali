# HyperOS 3 Notes (rothko)

## Version

- **HyperOS 3**, build `OS3.0.1.0.WNNMIXM` (beta track, as of 2026-10-11).
- Android 16 (SDK 36) base.

## Child-Process Toggle Location

On HyperOS 3 / Android 16, there is no user-facing "child process" toggle that affects `Runtime.exec()` from apps. The toggle referenced in older guides (Developer Options → "Disable child process restrictions") was an Android 14-era workaround for the USAP (unspecialized app process) pool and does not exist on this build.

If guest process spawning fails:
1. Check `adb logcat` for `avc: denied` lines with `scontext=u:r:untrusted_app_27`.
2. Verify the proot binary has the executable bit (`chmod 755`).
3. Do NOT disable SELinux or set permissive — the app must work in enforcing mode.

## SDK 36 Behaviour

- **Background process limits:** Android 16 enforces stricter background execution limits. The compat test runs in the foreground Activity; do not move it to a background service without a foreground notification.
- **Thermal API:** Use `PowerManager.getCurrentThermalStatus()`. Sysfs thermal reads are SELinux-denied (see DEVICE.md).
- **File access:** The app uses scoped storage (`getFilesDir()`). The Linux guest lives at `filesDir/linuxfs`. No `MANAGE_EXTERNAL_STORAGE` needed.

## HyperOS 4 Watch

Milo wants to be told when a HyperOS 4 build lands for rothko Global. As of 2026-10-15: no Global HyperOS 4 build observed (only internal/closed beta for EEA/China variants).
