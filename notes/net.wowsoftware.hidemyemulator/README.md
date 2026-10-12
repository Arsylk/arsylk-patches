# Hide My Emulator — net.wowsoftware.hidemyemulator

- **Package:** `net.wowsoftware.hidemyemulator`
- **Version:** `1.3` (`4`)
- **Download:** TODO — vendor-distributed. Licensing dependency is on Google Play: `com.wowsoftware.hmelicense` (https://play.google.com/store/apps/details?id=com.wowsoftware.hmelicense)
- **jadx mappings:** [`jadx-mappings.md`](jadx-mappings.md)

Emulator-hiding Xposed/LSPlant module with a Jetpack Compose UI. Premium (device masking) is
delegated to a **separate paid Play app**, `com.wowsoftware.hmelicense`. The gate (`o9.e`, log tag
`HookLicenseGate`) checks that app is installed with the expected signing certificate, runs an EC
(secp256r1) challenge/`client_pub` handshake against its ContentProvider
`content://com.wowsoftware.hmelicense.license/v1/license`, receives a
`SealedLicense{serverPublicKeyDer, iv, ciphertext}` (`aa.d`), verifies it (HmacSHA256 + EC, 5-minute
timestamp freshness) and caches the expiry in prefs as `license_until` (epoch millis). The mask
builder (`aa.f`) then applies the premium masks only while `license_until > now`. All licensing is
pure Java/Kotlin; the native `libnative_entry.so` is the spoofing engine, not the licence.

## Patches

| Patch | Target (fingerprint → method) | Change |
|---|---|---|
| `Unlock premium` | `LicenseGateFingerprint` → the mask builder's expiry check (`XSharedPreferences.getLong` + `SharedPreferences.getLong` + `System.currentTimeMillis` + `cmp-long` + `if-lez`; all non-obfuscable framework/Xposed anchors) | remove the `if-lez` that skips the premium mask block, so masks run unconditionally |

Why this target: it is the single DEX-level gate that enables masking, and it keys off framework/Xposed
call shape (the app encrypts its string pool, so the `license_until` key is not usable as an anchor).
Removing the `if-lez` is operand- and register-agnostic — it always falls through into the mask block
regardless of which register holds the expiry — so no licence-app contact or server is needed. The
handshake `o9.e` and the UI licence card are left untouched (cosmetic); the functional premium is the
mask block.

Validated: built to `.mpp`, applied with morphe-desktop to `release-1.3.apk`, the patched DEX
confirmed to run `cmp-long` straight into the mask block (no `if-lez`), **and confirmed on device** —
the app shows "Premium" with the masking protections active, no `com.wowsoftware.hmelicense` installed.
