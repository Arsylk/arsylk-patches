package dev.arsylk.fosspatch.patches.hidemyandroid

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

/**
 * The premium entitlement of Hide My Android collapses to a single native call:
 * `com.wowsoftware.hidemyandroid.jni.DeviceInfoProvider.evalBlobNative(...)` decodes the cached,
 * Ed25519-signed licence blob into a boolean. A thin Kotlin wrapper in the same class calls that
 * native method and returns its boolean result; that wrapper is what the rest of the app consults.
 *
 * The class and the native method name are hard-coded in the licensing `.so` (they are passed to
 * `RegisterNatives`) and therefore cannot be renamed by R8/ProGuard. The app also encrypts its
 * string pool, so string filters are useless here — this non-obfuscated JNI surface is the one
 * stable anchor.
 */
object EvalBlobWrapperFingerprint : Fingerprint(
    definingClass = "Lcom/wowsoftware/hidemyandroid/jni/DeviceInfoProvider;",
    returnType = "Z",
    filters = listOf(
        methodCall(name = "evalBlobNative"),
    ),
)

/**
 * The Kotlin method that invokes the native `bootstrapRuntime(...)`. `bootstrapRuntime` performs the
 * app-integrity check — it reads the running APK's signing certificate and `base.apk` hash and
 * compares them (locally and against the server). Because a patched APK is re-signed, that check
 * fails and the native code terminates the process shortly after startup.
 *
 * Morphe cannot patch the `.so`, but it can stop the DEX from calling into it. Anchored on the
 * non-obfuscable native method name.
 */
object BootstrapRuntimeCallerFingerprint : Fingerprint(
    definingClass = "Lcom/wowsoftware/hidemyandroid/jni/DeviceInfoProvider;",
    returnType = "V",
    filters = listOf(
        methodCall(name = "bootstrapRuntime"),
    ),
)

/**
 * The Kotlin method that invokes the native `installWatchdog(...)`, which starts the runtime
 * anti-tamper watchdog (it scans `/proc/self/maps`). Neutralised for the same reason as
 * [BootstrapRuntimeCallerFingerprint].
 */
object InstallWatchdogCallerFingerprint : Fingerprint(
    definingClass = "Lcom/wowsoftware/hidemyandroid/jni/DeviceInfoProvider;",
    returnType = "V",
    filters = listOf(
        methodCall(name = "installWatchdog"),
    ),
)
