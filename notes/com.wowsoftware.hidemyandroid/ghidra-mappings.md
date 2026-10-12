# ghidra / native mappings — 1.7.1 (arm64-v8a)

Two native libs, mapped for understanding how the app spoofs and gates. The crack is **DEX-only** and
does not touch native code (the DEX patch defeats the licence/anti-tamper without it — see README);
this map is RE reference, useful if a future version moves enforcement into native code. Offsets are
**hints** for the stated build/ABI; locate by the documented idiom on other builds.

## `libnative_entry.so` — the spoof engine (what actually applies most spoofs)

Loaded into every scoped target process. State is pushed from DEX by `NativeHooker.setNativeStateNative`;
the engine then **inline-hooks libc** (shadowhook/dobby; `resetprop` string present) to answer property
and file reads with the profile's values. This is where build props / serial / boot_id / install-time /
timezone / locale spoofs land.

| Addr | Symbol / role |
|---|---|
| `0x232cc` | `Java_com_wowsoftware_hidemyandroid_hook_entity_NativeHooker_setNativeStateNative` (sole JNI export) — receives the marshalled state |
| `0x231e0` | `native_init` — writes state into `.bss` (`0x68d70`/`0x68d98`) and installs the hooks |
| `fcn.0x23dec` | libc property-hook installer — targets `__system_property_get` (import `0x63210`, `.rodata` `0xb7b2`), `__system_property_find` (`0xcaae`), `__system_property_read_callback` (`0xac95`) |
| `fcn.0x29cc4` | hook-engine selector (`shadowhook`/`dobby` strings) |
| file hooks | `statx` `.rodata 0xb7c8`, `fstatat` `0xc78a`, `fstat` import `0x63400` (install-time); `/proc/sys/kernel/random/boot_id` `.rodata 0xb7f7` via `pread 0x63340`/`fdopen 0x632f0` |
| root/xposed strings (for `hook_hide_root`/`hook_hide_lsposed`) | `magisk 0xba24`, `daemonsu 0xc419`, `/data/adb 0xb915`, `xposed 0xaeef`, `zygisk 0xc562`, `/riru/ 0xab58` |

No telephony/SIM/operator/IMSI/wifi strings in this lib — those spoofs are **pure DEX YukiHook** (in
the target process), not native. `ro.build.version.sdk` handled @ `0xc212`.

## `libhidemyandroid.so` — licence/anti-tamper + tun2proxy

Licence functions: `evalBlobNative` is registered via `RegisterNatives` (not exported), resolved
through its name-string → triple → fn-pointer. Crypto is libsodium (Ed25519 + Blake2b +
XChaCha20-Poly1305). Also hosts the VPN tunnel engine:

| Addr | Symbol / role |
|---|---|
| `0x68e7c` | `Java_…_ProxyVpnService_startTun2ProxyNative` — loads `libhma_tun2proxy.so` (helper `fcn.0x69090`, `dlsym hma_tun2proxy_start`); `0x692b0` stop, `0x68e0c` isRunning, `0x69024` lastCode |

Licence/integrity functions (arm64-v8a):

| Addr | Symbol / role |
|---|---|
| `0x4f7f0` | `evalBlobNative` impl (RegisterNatives table @ `0x51d74`); return load @ `0x507e4` (`ldr w0,[sp,0x54]`) |
| `0x487b4` | `validateLicenseNative` (exported); online validate, cert-pinned |
| `0x44714` | `getLastValidatedBlobNative` (exported); returns cached blob |
| `0x614d4` | `bootstrapRuntime` (exported); app-integrity. memcmp `0x656b0` = signing-cert-hash vs server, `0x656c8` = base.apk-hash vs server (idiom: lowercase-hex loop → `bl memcmp; cmp w0,#0; cset w8,ne`) |
| `0x66e80` | `installWatchdog` (exported); loops `openat("/proc/self/maps")` + resolves `sendto` |
| `fcn.0x359a8` | TLS **SPKI pin** (`getServerCertificates`→`getPublicKey`→`getEncoded`); failure string `SSL_PIN_MISMATCH` @ `.rodata 0x140ed` |
| `fcn.0x5f760` | reads `getPackageName` + `getPackageInfo` + `getApkContentsSigners` (signing-cert SHA-256) |
| `fcn.0x5d2d0` | reads `ApplicationInfo.sourceDir` (path to `base.apk`) |
| `fcn.0x5b168` | libsodium core (Ed25519/Blake2b) used by `evalBlobNative` |

Server endpoints (pinned): `https://app.hidemyandroid.com/api/app/expected-apk-hash?version_code=171`,
`/api/app/expected-signing-cert-hash`, `/api/license/validate`.

## evalBlob / validate / integrity-memcmp locations per ABI (RE reference)

| ABI | evalBlob fn | validate entry | integrity memcmp sites |
|---|---|---|---|
| arm64-v8a | `0x4f7f0` | `0x487b4` | `0x656b0`, `0x656c8` |
| x86_64 | `0x4eab0` | `0x48390` | `0x64cb4`, `0x64ff3` |
| x86 | `0x567b0` | `0x4ff20` | `0x6d340`, `0x6d60e` |
| armeabi-v7a (Thumb) | `0x2e348` | `0x28e2c` | not a libc memcmp (inlined / Rust slice-eq) |
