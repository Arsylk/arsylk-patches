# jadx / DEX mappings — net.wowsoftware.hidemyemulator 1.3

R8-obfuscated Kotlin (Jetpack Compose). Names are 1.3-specific and change per build; strings are
decrypted at runtime (jadx shows `String decrypt:` annotations), so neither names nor strings are
reliable fingerprint anchors — fingerprint by framework/Xposed call shape.

## Licensing (pure Java/Kotlin — no native verification)

| Obfuscated | Meaning |
|---|---|
| `aa.f` | **mask builder + licence gate** — method `j(Object)`. Reads `license_until` (via `XSharedPreferences.getLong` then `SharedPreferences.getLong`) and applies the premium masks only while `license_until > System.currentTimeMillis()`. ← patch target |
| `o9.e` | licence handshake — log tag `HookLicenseGate`. Checks `com.wowsoftware.hmelicense` installed + signing cert; EC (secp256r1) challenge (`challenge`, `client_pub`) to `content://com.wowsoftware.hmelicense.license/v1/license`; receives a `SealedLicense`; verifies; writes prefs `license_until` |
| `o9.f` | licence-state holder used by `o9.e` (outcome types incl. granted / "Authentication failed") |
| `aa.d` | `SealedLicense(serverPublicKeyDer, iv, ciphertext)` — the signed/encrypted licence blob |
| `r9.a` | SharedPreferences accessor holding `license_until` (epoch millis); keys also `license_expiry_epoch_day`, `license_email` |

External dependency: `com.wowsoftware.hmelicense` (paid app on Google Play) — the licence authority;
also reachable via `market://details?id=com.wowsoftware.hmelicense`.

## Gate shape (patch anchor)

```
XSharedPreferences.getLong(key, 0)   ; false-branch read
SharedPreferences.getLong(key, 0)    ; license_until
System.currentTimeMillis()           ; now
cmp-long vDest, license_until, now
if-lez  vDest, :skip_masks           ; expired -> skip premium block   <-- removed by the patch
```

## Spoofing engine (not licence)

| Symbol | Meaning |
|---|---|
| `net.wowsoftware.hidemyemulator.hook.entity.NativeHooker` | applies masks via `setNativeStateNative(...)` into `libnative_entry.so` |
| `net.wowsoftware.hidemyemulator.hook.entity.RuntimeWatchdog` | runtime integrity watchdog |
| `lib/<abi>/libnative_entry.so` | native hook/mask engine (arm64-v8a, x86_64) |

No native licence code → no `ghidra-mappings.md` for this app (omitted per the notes format).
