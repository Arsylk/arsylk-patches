package dev.arsylk.fosspatch.patches.hidemyemulator

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/**
 * The app-wide licence state reducer. The licence repository collects the handshake result (a sealed
 * licence state) and converts it to the UI/feature model via this static method: when the state is
 * the "licensed" variant it returns a licensed model carrying the expiry (as an epoch-day), otherwise
 * it returns the singleton "unlicensed" model. The resulting model is pushed into the StateFlow that
 * both the licence UI and the protection toggles observe.
 *
 * The app encrypts its string pool, so the method is anchored on its distinctive framework time-API
 * calls — converting the expiry epoch-millis to an epoch-day via `ZoneId.systemDefault()` +
 * `Instant.ofEpochMilli()` + `LocalDate.toEpochDay()`. That trio is unique to this reducer.
 */
object LicenseStateReducerFingerprint : Fingerprint(
    filters = listOf(
        methodCall(definingClass = "Ljava/time/ZoneId;", name = "systemDefault"),
        methodCall(definingClass = "Ljava/time/Instant;", name = "ofEpochMilli"),
        methodCall(definingClass = "Ljava/time/LocalDate;", name = "toEpochDay"),
    ),
)

/**
 * The licence gate in the mask builder. It reads the cached licence expiry (`license_until`) from both
 * an `XSharedPreferences` and a `SharedPreferences`, compares it to `System.currentTimeMillis()`, and
 * applies the premium masks only while the expiry is in the future:
 *
 * ```
 *     XSharedPreferences.getLong(key, 0)   ; false-branch read
 *     SharedPreferences.getLong(key, 0)    ; license_until
 *     System.currentTimeMillis()           ; now
 *     cmp-long v0, license_until, now
 *     if-lez  v0, :skip_masks              ; expired -> skip premium block
 * ```
 *
 * The `license_until` prefs value is only written by a successful licence handshake, which never runs
 * offline, so this gate is patched separately from the UI state. Anchored on framework/Xposed call
 * shape (encrypted strings make the key unusable); the `XSharedPreferences.getLong` is what makes it
 * unique in the app.
 */
object LicenseGateFingerprint : Fingerprint(
    filters = listOf(
        methodCall(definingClass = "Lde/robv/android/xposed/XSharedPreferences;", name = "getLong"),
        methodCall(definingClass = "Landroid/content/SharedPreferences;", name = "getLong"),
        methodCall(definingClass = "Ljava/lang/System;", name = "currentTimeMillis"),
        opcode(Opcode.CMP_LONG),
        opcode(Opcode.IF_LEZ),
    ),
)
