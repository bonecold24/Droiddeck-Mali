# Device: Xiaomi 14T Pro (rothko)

## Model Identification

| Field | Value |
|---|---|
| Retail model | 2407FPN8EG (Global) |
| Codename | rothko |
| Device-reported model | Xiaomi A402XM (Japan variant, rothko_jp) |
| SoC | MediaTek Dimensity 9300+ (MT6989) |
| GPU | Immortalis-G720 MC12 (Mali CSF/v12) |
| RAM | 12 GB |

## OS (as of 2026-10-15 phone report)

| Field | Value |
|---|---|
| Android version | 16 (SDK 36) |
| Fingerprint | Xiaomi/rothko_jp/rothko:16/BP2A.250605.031.A3/OS3.0.1.0.WNNMIXM |
| HyperOS | 3 (OS3.0.1.0.WNNMIXM, beta track) |
| Kernel | 6.1.138-android14 |
| App SELinux context | untrusted_app_27 |

## SDK 36 Behaviour Notes

- **Thermal sysfs:** Reading `/sys/class/thermal/...` is SELinux-denied for `untrusted_app_27`. Use `PowerManager.getCurrentThermalStatus()` (Android API) instead. Report "unavailable" if the API returns `THERMAL_STATUS_NONE`.
- **GPU sysfs:** Reading `/sys/class/kgsl/...` or Mali GPU info nodes is SELinux-denied. Use `ActivityManager.getDeviceConfigurationInfo()` for GL version strings. Report "unavailable" instead of blank.
- **Battery temperature:** Use `BatteryManager.EXTRA_TEMPERATURE` (tenths of °C) via `ACTION_BATTERY_CHANGED` intent. This works without special permissions.
- **Child process:** On Android 16, `Runtime.exec()` from the app context works, but proot child processes inherit the app's SELinux context. No special toggle needed for basic exec; the "child-process" toggle referenced in older docs was for Android 14's `USAP` pool behaviour and does not apply.

## /dev/mali0 Access

- Node: `crw-rw-rw-` (world read/write)
- `os.open('/dev/mali0', O_RDWR)` succeeds inside the proot guest (observed 2026-10-15).
- No AVC denial captured for the open. Label: observed on phone, node access only.
- This does NOT imply the kbase ioctls succeed — see the Stage 0 kbase probe (VERSION_CHECK, SET_FLAGS) for UAPI validation.
