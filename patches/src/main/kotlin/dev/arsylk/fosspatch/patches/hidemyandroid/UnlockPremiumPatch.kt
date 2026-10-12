package dev.arsylk.fosspatch.patches.hidemyandroid

import dev.arsylk.fosspatch.patches.shared.Constants.COMPATIBILITY_HIDEMYANDROID
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Restores the premium feature set of Hide My Android (unlimited profiles, no daily-create cap,
 * full identifier spoofing, headless API) without a subscription or even an account, and works
 * fully offline.
 *
 * Premium runs through one native call, `DeviceInfoProvider.evalBlobNative(...)`, surfaced by a thin
 * Kotlin wrapper (see [EvalBlobWrapperFingerprint]). Four coordinated changes:
 *
 *  1. The wrapper is forced to return `true`, so the headless-API check and the in-process
 *     `NativeHooker` full-identifier-spoofing gate (which call it directly) report premium.
 *
 *  2. The volatile premium flag that the profile caps read is only set by the startup coroutine
 *     after a session token exists, so with no account it stays false. Every `iget-boolean` read of
 *     it is forced to `true` — so the caps lift regardless of login state.
 *
 *  3. The account badge and profile counter observe a separate StateFlow, which the account
 *     view-model fills with `false` on the account-less path. Every `Boolean.FALSE` load feeding
 *     that flow is rewritten to `Boolean.TRUE`, so the UI renders premium too.
 *
 *  4. The app self-checks its integrity in native code (`bootstrapRuntime` verifies the signing
 *     certificate and APK hash; `installWatchdog` starts an anti-tamper watchdog). A re-signed
 *     patched APK fails those checks and the native code terminates the process at startup. Morphe
 *     cannot patch the `.so`, so the two Kotlin methods that invoke them are turned into no-ops.
 *
 * Only the DEX is modified.
 */
@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock premium",
    description = "Unlocks the premium feature set offline, without a subscription.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_HIDEMYANDROID)

    execute {
        // 1. Native-verdict wrapper always reports premium (headless + NativeHooker paths).
        EvalBlobWrapperFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )

        // 2. Force every read of the premium flag field to true (UI badge + profile caps),
        //    independent of login/token.
        //
        //    The flag is the volatile boolean of the LicenseStore (`Lbb/g;->c:Z` in 1.7.1). It has no
        //    non-obfuscated anchor (encrypted strings; the field is set from the wrapper in a coroutine
        //    state machine that cannot be fingerprinted across its fragments), so the reference is
        //    pinned for this version. Every `iget-boolean` of it is rewritten to a constant true.
        val flagClass = "Lbb/g;"
        val flagName = "c"
        fun isFlagRead(instruction: com.android.tools.smali.dexlib2.iface.instruction.Instruction): Boolean {
            if (instruction.opcode != Opcode.IGET_BOOLEAN) return false
            val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return false
            return reference.definingClass == flagClass && reference.name == flagName
        }

        classDefForEach { classDef ->
            classDef.methods.forEach forEachMethod@{ method ->
                val original = method.implementation ?: return@forEachMethod
                val indices = original.instructions.withIndex()
                    .filter { isFlagRead(it.value) }
                    .map { it.index }
                if (indices.isEmpty()) return@forEachMethod

                val mutableMethod = mutableClassDefBy(classDef).methods.first {
                    it.name == method.name &&
                        it.returnType == method.returnType &&
                        it.parameterTypes.toList() == method.parameterTypes.toList()
                }
                // Rewrite from last to first so earlier indexes stay valid.
                indices.sortedDescending().forEach { index ->
                    val register = mutableMethod.getInstruction<OneRegisterInstruction>(index).registerA
                    mutableMethod.replaceInstruction(index, "const/4 v$register, 0x1")
                }
            }
        }

        // 3. Force the premium StateFlow to emit `true`, so the account badge and the profile
        //    counter render premium even with no account.
        //
        //    The field read in step 2 drives enforcement (profile caps, headless/spoof gates), but the
        //    UI badge ("Free Account" / "x/5") observes a separate StateFlow, `Lpa/v0;->j:Lfd/h0;`.
        //    The account view-model pushes `Boolean.FALSE` into it on the guest path (no session token)
        //    and on a failed-validation path; both emissions, plus the field initialiser, load the
        //    value with `sget-object ... Ljava/lang/Boolean;->FALSE`. Every such `FALSE` load that sits
        //    next to an access of the `j` flow field is rewritten to `TRUE`, so the flow carries
        //    premium regardless of login state. Reference pinned for 1.7.1 (encrypted strings; the
        //    emissions are spread across a coroutine state machine with no fingerprintable anchor).
        val flowClass = "Lpa/v0;"
        val flowField = "j"
        fun isFlowAccess(instruction: com.android.tools.smali.dexlib2.iface.instruction.Instruction): Boolean {
            if (instruction.opcode != Opcode.IGET_OBJECT && instruction.opcode != Opcode.IPUT_OBJECT) return false
            val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return false
            return reference.definingClass == flowClass && reference.name == flowField
        }
        fun isBooleanFalse(instruction: com.android.tools.smali.dexlib2.iface.instruction.Instruction): Boolean {
            if (instruction.opcode != Opcode.SGET_OBJECT) return false
            val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return false
            return reference.definingClass == "Ljava/lang/Boolean;" && reference.name == "FALSE"
        }

        classDefForEach { classDef ->
            if (classDef.type != flowClass) return@classDefForEach
            classDef.methods.forEach forEachMethod@{ method ->
                val original = method.implementation ?: return@forEachMethod
                val list = original.instructions.toList()
                // A `FALSE` load feeds the premium flow if a `j`-field access is within a few
                // instructions of it (the load, the flow `iget`/`iput`, and the emit are adjacent).
                val indices = list.indices.filter { i ->
                    isBooleanFalse(list[i]) &&
                        (maxOf(0, i - 6)..minOf(list.lastIndex, i + 6)).any { isFlowAccess(list[it]) }
                }
                if (indices.isEmpty()) return@forEachMethod

                val mutableMethod = mutableClassDefBy(classDef).methods.first {
                    it.name == method.name &&
                        it.returnType == method.returnType &&
                        it.parameterTypes.toList() == method.parameterTypes.toList()
                }
                indices.sortedDescending().forEach { index ->
                    val register = mutableMethod.getInstruction<OneRegisterInstruction>(index).registerA
                    mutableMethod.replaceInstruction(
                        index,
                        "sget-object v$register, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;",
                    )
                }
            }
        }

        // 4. Force every DEX `DeviceInfoProvider.evalBlobNative(...)` gate to true.
        //
        //    A whole class of features (SIM identity in ta.o0; IMEI/GAID/OAID/MediaDRM/AppSetId in
        //    ta.q; Bluetooth MAC; the AccountManager suite in ta.v0; LAN-scan-block; WiFi SSID/BSSID)
        //    is coded but never places its hooks because the hook-registrar branch is gated on a
        //    DIRECT native `evalBlobNative(...)` call (not the `f()` wrapper), and that native call is
        //    not honoured at these sites even with the `.so` patched. Each gate is
        //    `invoke evalBlobNative; move-result vX; if-eqz vX, :skip`. Override the boolean result of
        //    every such call with a constant `true`, so the registrars place their hooks.
        fun isEvalBlob(instruction: com.android.tools.smali.dexlib2.iface.instruction.Instruction): Boolean {
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return false
            return reference.definingClass == "Lcom/wowsoftware/hidemyandroid/jni/DeviceInfoProvider;" &&
                reference.name == "evalBlobNative"
        }
        classDefForEach { classDef ->
            classDef.methods.forEach forEachMethod@{ method ->
                val original = method.implementation ?: return@forEachMethod
                val list = original.instructions.toList()
                val moveResultIndices = list.indices.filter { i ->
                    i > 0 && isEvalBlob(list[i - 1]) && list[i].opcode == Opcode.MOVE_RESULT
                }
                if (moveResultIndices.isEmpty()) return@forEachMethod

                val mutableMethod = mutableClassDefBy(classDef).methods.first {
                    it.name == method.name &&
                        it.returnType == method.returnType &&
                        it.parameterTypes.toList() == method.parameterTypes.toList()
                }
                moveResultIndices.sortedDescending().forEach { index ->
                    val register = mutableMethod.getInstruction<OneRegisterInstruction>(index).registerA
                    val constInsn = if (register < 16) "const/4 v$register, 0x1" else "const/16 v$register, 0x1"
                    mutableMethod.replaceInstruction(index, constInsn)
                }
            }
        }

        // 5. Force the cached-licence-blob field non-empty so the Tier-2 gate passes.
        //
        //    The advanced-identifier registrars (IMEI/GAID/OAID/MediaDRM in ta.q, the SIM suite in
        //    ta.o0, Bluetooth MAC, the AccountManager suite in ta.v0) wrap their hook registration in
        //    `if (jVar.str.length() > 0) { if (evalBlobNative(jVar.str, ...) && ...) { ... } }`, where
        //    `jVar` is the single `wa.j` identity built in the framework hook manager and `jVar.str`
        //    (smali field `Lwa/j;->a`) is the cached signed-licence blob. With no licence the blob is
        //    empty, so the whole Tier-2 block is skipped regardless of the `f()` patch. The spoof
        //    values come from the profile config (e.g. `simConfig`), not from the blob, so forcing the
        //    field to a non-empty constant unlocks the whole tier. Ref pinned to 1.7.1 (`Lwa/j;->a`).
        classDefForEach { classDef ->
            if (classDef.type != "Lwa/j;") return@classDefForEach
            classDef.methods.filter { it.name == "<init>" }.forEach forEachCtor@{ method ->
                val impl = method.implementation ?: return@forEachCtor
                val index = impl.instructions.withIndex().firstOrNull { (_, insn) ->
                    insn.opcode == Opcode.IPUT_OBJECT &&
                        ((insn as? ReferenceInstruction)?.reference as? FieldReference)?.let {
                            it.definingClass == "Lwa/j;" && it.name == "a"
                        } == true
                }?.index ?: return@forEachCtor

                val mutableMethod = mutableClassDefBy(classDef).methods.first {
                    it.name == method.name &&
                        it.parameterTypes.toList() == method.parameterTypes.toList()
                }
                val valueReg = mutableMethod.getInstruction<TwoRegisterInstruction>(index).registerA
                // Insert directly before the store, so the field receives the constant.
                mutableMethod.addInstructions(index, "const-string v$valueReg, \"1\"")
            }
        }

        // 6. Neutralise the native anti-tamper so the re-signed APK is not killed at startup.
        BootstrapRuntimeCallerFingerprint.method.addInstructions(0, "return-void")
        InstallWatchdogCallerFingerprint.method.addInstructions(0, "return-void")
    }
}
