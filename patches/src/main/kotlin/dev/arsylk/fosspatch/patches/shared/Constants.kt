package dev.arsylk.fosspatch.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

object Constants {
    /**
     * Hide My Android — device-information spoofing app (Xposed/YukiHook module) whose premium
     * tier gates functionality behind a server-issued, Ed25519-signed licence blob.
     *
     * Confirmed working on 1.7.1 (versionCode 171). `version = null` is declared experimentally so
     * the patch is also offered on later builds; it keys off the non-obfuscated JNI surface of
     * `com.wowsoftware.hidemyandroid.jni.DeviceInfoProvider`, which cannot be renamed by R8, so it
     * is expected to survive updates that only change obfuscated names.
     */
    val COMPATIBILITY_HIDEMYANDROID = Compatibility(
        name = "Hide My Android",
        packageName = "com.wowsoftware.hidemyandroid",
        apkFileType = ApkFileType.APK,
        // App icon is predominantly green.
        appIconColor = 0x3DDC84,
        targets = listOf(
            AppTarget(
                version = "1.7.1",
                versionCodes = mapOf(
                    SupportedAbi.ARM64_V8A to 171,
                    SupportedAbi.ARMEABI_V7A to 171,
                ),
            ),
            AppTarget(
                version = null,
                isExperimental = true,
            ),
        ),
    )

    /**
     * Hide My Emulator — emulator-hiding Xposed/LSPlant module. Premium (device masking) is licensed
     * through a separate paid Google Play app, `com.wowsoftware.hmelicense`, and cached locally as an
     * expiry timestamp; masking runs only while that timestamp is in the future.
     *
     * Confirmed working on 1.3 (versionCode 4). `version = null` is experimental; the patch keys off
     * the framework/Xposed call shape of the expiry check, not obfuscated names, so it should survive
     * updates that only reshuffle obfuscation.
     */
    val COMPATIBILITY_HIDEMYEMULATOR = Compatibility(
        name = "Hide My Emulator",
        packageName = "net.wowsoftware.hidemyemulator",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x3DDC84,
        targets = listOf(
            AppTarget(
                version = "1.3",
                versionCodes = mapOf(
                    SupportedAbi.ARM64_V8A to 4,
                    SupportedAbi.X86_64 to 4,
                ),
            ),
            AppTarget(
                version = null,
                isExperimental = true,
            ),
        ),
    )
}
