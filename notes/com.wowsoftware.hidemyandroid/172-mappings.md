# 1.7.2 (versionCode 172) re-derivation — com.wowsoftware.hidemyandroid

Obfuscation mapping for the 1.7.2 build, derived by jadx decompile + structural xref from
the stable `DeviceInfoProvider` JNI surface. Use this to re-derive patches for 1.7.3+.

Vendor-original SHA-256 (matches `https://app.hidemyandroid.com/api/app/expected-apk-hash?version_code=172`):
`33a197b5ea8241d9c949ca80faacbeb479b69d25a5462b7c793e5bcf62979b10`.

## Stable anchors (JNI — not renameable by R8)

| Member | Role | Change from 1.7.1 |
|---|---|---|
| `Lcom/wowsoftware/hidemyandroid/jni/DeviceInfoProvider;` | the JNI bridge | unchanged |
| `evalBlobNative(String,String,JJ,String,JJ)Z` | direct native licence-blob check | +2 params (String, long) |
| `f(String,String,Lra/o;JJ)Z` | Kotlin wrapper that calls `evalBlobNative` | second-class type: `Lqa/n;` → **`Lra/o;`** |
| `bootstrapRuntime(Context,ZI)V` | signing-cert + APK-hash integrity check | unchanged |
| `installWatchdog(Z)V` | `/proc/self/maps` anti-tamper watchdog | unchanged |
| caller of `bootstrapRuntime` | `DeviceInfoProvider.i(Context)V` | unchanged (name `i` survived) |
| caller of `installWatchdog` | `DeviceInfoProvider.j()V` | unchanged (name `j` survived) |

## Pinned obfuscated references (per-version; re-derive per build)

| 1.7.1 | 1.7.2 | Semantic role |
|---|---|---|
| `Lbb/g;` | **`Lcb/h;`** | `LicenseStore` (jadx recovers the name; field is `c:Z` volatile premium flag) |
| `Lbb/g;->c:Z` | **`Lcb/h;->c:Z`** | the premium flag (volatile boolean read by UI/programmatic gates) |
| `Lwa/j;` | **`Lxa/k;`** | the cached-licence-blob identity (built in the framework hook manager) |
| `Lwa/j;->a:Ljava/lang/String;` | **`Lxa/k;->a:Ljava/lang/String;`** | the cached signed-licence blob field |
| `Lpa/v0;` | **`Lw/e0;`** | account view-model (abstract) holding the premium `StateFlow` |
| `Lpa/v0;->j:Lfd/h0;` | **`Lw/e0;->w:Lfd/e;`** | the premium `StateFlow` (concrete subclasses: `Lw/i0;`, `Lw/b1;`) |
| tier-2 registrars `ta/*` | **`ua/*`** + **`ra/h`** | the 19 direct `evalBlobNative` call sites (was 20 in 1.7.1) |

## How the 1.7.2 pins were located

All from the stable `DeviceInfoProvider` surface — no jadx hand-classification needed:

1. **Tier-2 identity (`Lxa/k;->a`)** — `iget-object ... ->a:Ljava/lang/String;` is referenced
   from the tier-2 registrars (`ua/*`, `ra/*`) with the same `if (str.length() > 0)`
   `invoke length()` pattern. The defining class is the new identity. Confirmed: `<init>(String,String,Lra/o;JJ)V`
   does `iput-object p1, p0, Lxa/k;->a` at line 55.

2. **LicenseStore premium flag (`Lcb/h;->c`)** — the `qa/v0` `invokeSuspend` coroutine calls
   `DeviceInfoProvider.f(...)Z` once and `iput-boolean` into `Lcb/h;->c:Z`. jadx recovers
   `Lcb/h;` = `LicenseStore` (string-decrypt comment matches 1.7.1), so the volatile
   boolean is the same semantic field, just under a new package.

3. **Premium `StateFlow` (`Lw/e0;->w`)** — `Lw/e0;` is the only `ViewModel` subclass
   (`extends b2.n implements b2.r1, b2.l`) in the account area with a `Lfd/e;` field.
   The Boolean.FALSE-adjacency rewrite heuristic that works in 1.7.1 (`Lpa/v0;`) does
   **not** fire on 1.7.2 — the 1.7.2 emissions to the flow are spread across coroutine
   state machines with no fingerprintable anchor, so the UI-badge "Premium" label may not
   flip on 1.7.2. The functional tier-2 hooks and the LicenseStore flag still unlock.

## What the 1.7.2 patch actually does (5 of 6 steps; step 3 intentionally skipped)

| Step | What | Pin / anchor | Verified on 1.7.2 patched DEX |
|---|---|---|---|
| 1 | `DeviceInfoProvider.f` returns `true` | `EvalBlobWrapperFingerprint` (non-obfuscated) | head = `const/4 v0, 0x1; return v0` |
| 2 | every `iget-boolean Lcb/h;->c:Z` → `const/4 1` | pin above | 0 remaining reads; rewrite landed |
| 3 | premium `StateFlow` Boolean.FALSE → TRUE | skipped on 1.7.2 | 332 `Boolean.FALSE` loads remain (unchanged) |
| 4 | every `evalBlobNative` `move-result` → `const/4 1` | non-obfuscated (19 call sites) | 0 remaining move-results |
| 5 | `Lxa/k;-><init>` `iput-object a` preceded by `const-string "1"` | pin above | `const-string p1, "1"` at line 54 before `iput-object ... ->a` at 58 |
| 6 | `i(Context)` and `j()` heads = `return-void` | `Bootstrap/InstallWatchdog` fingerprints (non-obfuscated) | both confirmed |
