# HMA 1.7.1 — feature → code ground-truth map

Produced by a multi-agent RE workflow (9 clusters × trace+adversarial-verify) against **runtime
ground truth**: the exact set of 115 methods HMA actually hooked, captured from the device's
Vector/LSPosed boot log (`hooks_runtime.txt`), cross-checked with jadx DEX, full smali, and r2 on
both arm64 `.so`s. "Works" = the hook was **actually placed at runtime** in the captured session (not
merely present in code). Profile "nYa".

> **Two snapshots.** The ✅/❌ split below is the **stock capture** (patch steps 1–3 only, before the
> Tier-2 gate was forced). Steps **4 + 5** were then added and **re-verified on device** — the entire
> ❌ "dormant" set except the two genuinely-dead config fields now places and works (hook count
> 92 → 158). So read ❌ as "dormant on the stock/Tier-1 build; unlocked by 4 + 5 unless marked
> *genuinely dead*". See the README for the two-tier model.

Two native engines:
- `libnative_entry.so` — the spoof engine. Installs **inline hooks on libc `__system_property_get` /
  `__system_property_find` / `__system_property_read_callback`** (via shadowhook/dobby; `resetprop`
  string present) and on file syscalls (`statx`/`fstatat`/`fstat`, `pread`/`fdopen`). State is pushed
  in from DEX by `NativeHooker.setNativeStateNative(...)` @ `0x232cc`.
- `libhidemyandroid.so` — license/anti-tamper (evalBlobNative, bootstrapRuntime) **and** the
  tun2proxy VPN engine (`startTun2ProxyNative` @ `0x68e7c`).

## ✅ Actually works at runtime (hook placed, verified)

| Feature | Layer | Mechanism / code |
|---|---|---|
| **Build model/brand/fingerprint** (`deviceMockConfig`) | native | `ro.product.*`/`ro.build.*` via `__system_property_get` hook. `NativeHooker.k()` → `setNativeStateNative`. **This is the "Infinix Note 40".** |
| **serial** | native | `ro.serialno` property hook; gated by patched `DeviceInfoProvider.f()` (DEX) + `hook_identifiers_fully`. |
| **bootId** | native | `/proc/sys/kernel/random/boot_id` file interceptor (`pread`/`fdopen`); `.rodata` @ `0xb7f7`. |
| **timezone** (`hook_geo_timezone`) | native | `persist.sys.timezone` property hook. `NativeHooker.java:158`. |
| **locale** (`hook_geo_locale`) | native | `persist.sys.locale` + `ro.product.locale`. `NativeHooker.java:169` → `wa/d0.java:351-372`. |
| **install time** (`installTimeOffsetMillis`, `first/last_install_time`) | dex+native | DEX `getPackageInfo` rewrite (`ta/b0.java`) + native `statx`/`fstatat`/`fstat`. |
| **GPS / location** (`hook_geo_gps`, `geoConfig` lat/lng) | dex | `ta/y.java` hooks `Location.get{Latitude,Longitude,Altitude,Accuracy,Speed,Bearing}`, `LocationManager.getLastKnownLocation`, GNSS/Nmea. All in runtime list. |
| **cell location** | dex | `TelephonyManager.getCellLocation/getAllCellInfo/getNeighboringCellInfo/listen` (placed with the location batch; no own flag). |
| **sensors — realistic** (`hook_realistic_sensor`, `lightScenario`) | dex+native | 6× `SensorManager.registerListener` + native `ASensorEventQueue` path. |
| **nearby wifi** (`hook_nearby_wifi`) | dex | `WifiManager.getScanResults()` (per-SSID generated spoof via ContentProvider `get_or_create_scan_spoof`). |
| **nearby bluetooth** (`hook_nearby_bluetooth`) | dex | `BluetoothAdapter.isEnabled` + 4× `BluetoothLeScanner.startScan`. |
| **installer spoof** (`hook_spoof_installer`) | dex | `getInstallerPackageName` → `com.android.vending`. `ta/b.java`. |
| **hide root** (`hook_hide_root`) | dex+native | PM list filter via **hardcoded** root-app set (`wa/w.java`) + native magisk/su string hooks. |
| **hide LSPosed** (`hook_hide_lsposed`) | dex+native | `DexFile.openDexFile/openInMemoryDexFiles` + native xposed/zygisk strings. |
| **per-app anti-detection** (~31 hooks) | dex | Obfuscated hooks into target apps' own methods (e.g. `pl.nask.mobywatel`), placed by a **dynamic per-package ruleset**, not `hookFlags`. Foreign classes (0 hits in HMA dex). |
| **proxy / tun2proxy** (`proxyConfig`) | native | `ProxyVpnService` → `startTun2ProxyNative` (`libhidemyandroid.so`, loads `libhma_tun2proxy.so`). Works when `proxyConfig.enabled` (false in profile). |

## Dormant on the stock build — unlocked by steps 4 + 5 (except *genuinely dead*)

> **UPDATE — Tier-2 now unlocked.** The rows below marked "gated by `evalBlobNative`" / "cached
> licence blob" (**SIM, IMEI/IMEI2, GAID, OAID, MediaDRM, Bluetooth MAC, virtual accounts**, and
> `android_id`/the Settings-gated ones) were dormant only because of the licence-blob gate
> `if (jVar.str.length() > 0) { if (evalBlobNative(…) && …) }`. Patch **steps 4 + 5** (force
> `evalBlobNative`→true and `Lwa/j;->a` non-empty) open that gate; **verified on device** — the full
> SIM/IMEI/GAID/MediaDRM/BT-MAC/AccountManager hook set now places and SIM spoofs (Plus/PL → the
> profile's Banglalink/BD). They draw their spoof values from the profile config.
>
> **Re-verified after 4+5 (hook count 92 → 158): the rest of the "dormant" rows were ALSO just
> blob-gated and now place too** — `hook_hide_vpn` (ConnectivityManager.getActiveNetwork/getAllNetworks/
> NetworkInfo), `hook_hide_proxy` (System.getProperty), `hook_hide_dev_opts`/`hook_hide_airplane_mode`
> (Settings.getInt/getString), `hook_lan_scan_block` (Socket.connect/InetAddress.isReachable), WiFi
> `getSSID`/`getBSSID`, and the sensor synthesis (SystemSensorManager.getFullSensorList/
> dispatchSensorEvent). So nearly the entire feature set is real-but-paywalled, unlocked by 4+5.
>
> **The only genuinely dead config (no consumer at all — not gated):** `hiddenPackages` (profile list
> ignored; a hardcoded 12-app root set in `wa/w.java` is used instead) and `backupPackages` (zero
> consumers). `hook_spoof_uptime` is simply toggled off in this profile (works when enabled).

Column "Gate (stock)" = why it was dormant on the Tier-1 build; all are **unlocked by steps 4 + 5**
unless the row says **GENUINELY DEAD**.

| Feature | Gate (stock) | Code |
|---|---|---|
| **android_id** | `Settings.Secure.getString` not placed on stock; opens with step 4 (the `evalBlobNative` gate). | `ta/q.java:101-105`. |
| **GAID** | `AdvertisingIdClient.getAdvertisingIdInfo` not placed; gated by direct `evalBlobNative && f5559m`. | `ta/q.java:553-556`. |
| **OAID** | OEM `getOAID/getId` not placed. | `ta/q.java:1740-1781`. |
| **MediaDRM id** | `MediaDrm.getPropertyByteArray` not placed. | `ta/q.java:1034-1039`. |
| **IMEI / IMEI2** | `TelephonyManager.getImei()` not placed (only cell-location telephony is). | `ta/q.java:1004-1015`. |
| **bootCount, app_set_id** | `Settings.Global.getInt` / AppSetId `getId` not placed. | `ta/q.java:1041-1052`. |
| **SIM identity** (`simConfig`: operator/MCCMNC/country/IMSI/ICCID/phone) | **Full ~30-getter suite coded** in `ta/o0.java` i() but **never placed**. Gate `evalBlobNative && e2.b() && e2.d()`; instantiation gate `r8/a.smali:5289`. | `ta/o0.java:326-401`. |
| **WiFi SSID/BSSID** (`hook_wifi`, `ssid`, `bssid`) | `WifiInfo.getSSID/getBSSID` not placed. (The scan-remap generates its own spoofs, ignores profile `ssid`/`bssid`.) | `ta/z0.java:44-48,97-101`. |
| **Bluetooth MAC** (`bluetoothMac`) | `BluetoothAdapter.getAddress` not placed. | `ta/q.java:1711-1713`. |
| **virtual accounts** (`hook_virtual_accounts`) | Full `AccountManager` suite coded + dispatched, premium-gated, not firing in capture. | `ta/v0.java`. |
| **LAN scan block** (`hook_lan_scan_block`) | `Socket.connect`/`InetAddress.isReachable` not placed. | `ta/j.java:271-290`. |
| **hide VPN** (`hook_hide_vpn`) | `ConnectivityManager`/`NetworkInterface` not placed; no native handler. | `ta/o.java`. |
| **hide proxy** (`hook_hide_proxy`) | `System.getProperty` not placed; not marshalled to native. | `ta/l.java` case 0. |
| **hide dev-opts** (`hook_hide_dev_opts`) | `Settings` getInt/getString + `isXposedActive` not placed. | `ta/i.java`, `ta/j0.java`. |
| **hide airplane** (`hook_hide_airplane_mode`) | `Settings.Global/System` getInt not placed. | `ta/j.java` case 0. |
| **sensor synthesis** (realistic + per-type) | On stock the `ta.h0`/`ta.i0` synth hooks aren't placed; step 4 opens their blob gate too — after 4+5 `getFullSensorList`/`dispatchSensorEvent` place. (Per-type flags are still coupled to realistic mode in `wa/d0.java`; realistic is the dominant path.) | `wa/d0.java`, `ta/h0.java`, `ta/i0.java`. |
| **spoof uptime** (`hook_spoof_uptime`, `uptimeOffsetMillis`) | **Not a gate** — complete `SystemClock` hook, just **toggled `false`** in this profile. Works when the toggle is on. | `ta/q0.java`. |
| **hiddenPackages** | **GENUINELY DEAD** — the profile list is never read; a **hardcoded** 12-entry root-app set (`wa/w.java`) is used instead. Steps 4+5 do not change this. | `wa/w.java` (hardcoded); profile field unused. |
| **backupPackages** | **GENUINELY DEAD** — zero consumers anywhere. | `oa/y1.java` field only. |

## The key pattern (RESOLVED)

The entire dormant set shared one gate: the registrars wrap hook placement in
`if (jVar.str.length() > 0) { if (evalBlobNative(jVar.str, …) && …) { … } }`, where `jVar.str`
(`Lwa/j;->a`) is the cached licence blob (empty with no licence). The Tier-1 `f()` patch does **not**
help, because these call `evalBlobNative` **directly**. The byte-patched `.so` `evalBlobNative`→1 also
did **not** open them (ineffective at these DEX call sites). The fix that worked, **verified on
device**: DEX **step 4** (force every `evalBlobNative` call result → `true`) + **step 5** (force
`Lwa/j;->a` non-empty so `jVar.str.length() > 0` passes). Result: the full SIM/IMEI/GAID/OAID/MediaDRM/
BT-MAC/AccountManager/WiFi/VPN/proxy/dev-opts/airplane/LAN/sensor set places (hook count 92 → 158) and
SIM spoofs (Plus/PL → the profile's Banglalink/BD). Values come from the profile config throughout.

## Corrections to earlier session claims

1. **android_id "spoofed" — was wrong on the stock build, now true.** On Tier-1 no `Settings.Secure`
   hook was placed, so the probe showed the app's **real per-app SSAID** (which matched the profile
   only because HMA seeds per-app configs from real values). After **step 4** the `Settings.getString`
   hook places and android_id is genuinely spoofed.
2. **SIM "unimplemented / would need reimplementing" — was wrong.** It is fully coded in `ta.o0`, just
   blob-gated; steps 4 + 5 unlock it. No new hooks were written.
3. **"The native `.so` patch is required to make hooks fire" — was wrong.** The DEX gate force (4 + 5)
   is the actual unlock; a DEX-only build (the `.mpp` alone) was tested on device and covers
   everything, so no native `.so` edit is needed (see README).
