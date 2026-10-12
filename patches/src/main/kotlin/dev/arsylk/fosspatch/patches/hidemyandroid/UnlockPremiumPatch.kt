package dev.arsylk.fosspatch.patches.hidemyandroid

import dev.arsylk.fosspatch.patches.shared.Constants.COMPATIBILITY_HIDEMYANDROID_171
import dev.arsylk.fosspatch.patches.shared.Constants.COMPATIBILITY_HIDEMYANDROID_172
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Per-version pinned obfuscated references. Non-obfuscated anchors
 * ([EvalBlobWrapperFingerprint], [BootstrapRuntimeCallerFingerprint],
 * [InstallWatchdogCallerFingerprint], and the `evalBlobNative` method name in step 4) survive
 * across versions because they key off the `DeviceInfoProvider` JNI surface, which R8 cannot
 * rename. The three pins below are the only things that move between obfuscation passes.
 */
private data class HmaPins(
    /** LicenseStore premium flag — read in step 2 (rewritten to `const/4 1`). */
    val flagClass: String,
    val flagName: String,
    /** Cached signed-licence blob identity — step 5 inserts `const-string "1"` before the
     *  `iput-object` into its `<init>`. */
    val blobClass: String,
    /**
     * 1.7.2+ only: the obfuscated class that holds the helper creating the premium
     * `MutableStateFlow` via the kotlinx factory `Ll6/c;->a(IILfd/a;)Lfd/e;`. The Boolean initial
     * value (the factory's 3rd arg) is rewritten to `0x1` so the flow starts as premium. Runtime
     * FALSE emissions to the flow go through coroutine `update` lambdas that are guarded by the
     * LicenseStore flag rewritten in step 2, so the flow stays TRUE. `null` to skip this step
     * (1.7.1, where the Boolean.FALSE-adjacency rewrite in step 3 already covers the flow).
     */
    val flowInitClass: String? = null,
)

/**
 * Shared implementation. `rewritePremiumFlow` is `false` on 1.7.2 because the Boolean.FALSE
 * adjacency heuristic for the premium `StateFlow` does not fire on the restructured 1.7.2
 * emissions; the other 5 steps (1, 2, 4, 5, 6) still apply.
 */
private fun BytecodePatchContext.applyHmaUnlock(pins: HmaPins, rewritePremiumFlow: Boolean) {
    // 1. Native-verdict wrapper always reports premium (headless + NativeHooker paths).
    EvalBlobWrapperFingerprint.method.addInstructions(
        0,
        """
            const/4 v0, 0x1
            return v0
        """,
    )

    // 2. Force every read of the LicenseStore premium flag to true.
    //
    //    The flag is the volatile boolean of the LicenseStore (a per-version pin; see [HmaPins]).
    //    It has no non-obfuscated anchor (encrypted string pool; the flag is set from the wrapper
    //    in a coroutine state machine that cannot be fingerprinted across its fragments), so the
    //    reference is pinned for this version. Every `iget-boolean` of it is rewritten to a
    //    constant `true`.
    val flagClass = pins.flagClass
    val flagName = pins.flagName
    fun isFlagRead(instruction: Instruction): Boolean {
        if (instruction.opcode != Opcode.IGET_BOOLEAN) return false
        val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            ?: return false
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

    // 3. (1.7.1 only) Force the premium StateFlow to emit `true`, so the account badge and the
    //    profile counter render premium with no account. The flow is a per-version pin; on 1.7.2
    //    the emissions are restructured and the Boolean.FALSE adjacency heuristic does not match,
    //    so the rewrite is skipped.
    if (rewritePremiumFlow) {
        // NOTE: the flow pin is not part of HmaPins because 1.7.2 skips this step entirely.
        // Keep the 1.7.1 pins here.
        val flowClass171 = "Lpa/v0;"
        val flowField171 = "j"
        fun isFlowAccess(instruction: Instruction): Boolean {
            if (instruction.opcode != Opcode.IGET_OBJECT && instruction.opcode != Opcode.IPUT_OBJECT) return false
            val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                ?: return false
            return reference.definingClass == flowClass171 && reference.name == flowField171
        }
        fun isBooleanFalse(instruction: Instruction): Boolean {
            if (instruction.opcode != Opcode.SGET_OBJECT) return false
            val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                ?: return false
            return reference.definingClass == "Ljava/lang/Boolean;" && reference.name == "FALSE"
        }

        classDefForEach { classDef ->
            if (classDef.type != flowClass171) return@classDefForEach
            classDef.methods.forEach forEachMethod@{ method ->
                val original = method.implementation ?: return@forEachMethod
                val list = original.instructions.toList()
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
    }

    // 3b. (1.7.2+) Rewrite the premium `MutableStateFlow`'s initial value to TRUE.
    //
    //    The Boolean.FALSE-adjacency rewrite above does not fire on 1.7.2 (the emissions go
    //    through coroutine `update` lambdas with the Boolean.FALSE `sget-object` scattered
    //    across state-machine fragments that cannot be tied back to the premium flow without
    //    a full dataflow pass). Instead, we force the flow to START as TRUE by overwriting the
    //    Boolean register passed to the kotlinx `MutableStateFlow` factory
    //    `Ll6/c;->a(IILfd/a;)Lfd/e;` — a `const/4 v?, 0x1` inserted directly before the
    //    `invoke-static` overwrites the FALSE the code loaded. (Remove+reinsert was avoided:
    //    `removeInstruction` + `addInstructions` at the same index dropped the narrow literal
    //    back to 0 in this patcher build, so we insert a fresh overwrite load instead.)
    //    The guest-path runtime emissions to the flow read the LicenseStore flag (rewritten to
    //    TRUE in step 2) and never fire the FALSE branch, so the flow stays TRUE.
    val flowInitClass = pins.flowInitClass
    if (flowInitClass != null) {
        classDefForEach { classDef ->
            if (classDef.type != flowInitClass) return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            classDef.methods.forEach forEachInitMethod@{ method ->
                val impl = method.implementation ?: return@forEachInitMethod
                val original = impl.instructions.toList()
                for ((i, insn) in original.withIndex()) {
                    val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference
                        ?: continue
                    if (ref.definingClass != "Ll6/c;") continue
                    if (ref.name != "a") continue
                    if (ref.parameterTypes.size != 3 || ref.parameterTypes[2] != "Lfd/a;") continue
                    // The factory's 3rd arg register holds the Boolean initial value.
                    // Insert a TRUE load right before the call, overwriting whatever was there.
                    val initReg = (insn as FiveRegisterInstruction).registerE
                    val mutableMethod = mutable.methods.first {
                        it.name == method.name &&
                            it.returnType == method.returnType &&
                            it.parameterTypes.toList() == method.parameterTypes.toList()
                    }
                    mutableMethod.addInstructions(i, "const/4 v$initReg, 0x1")
                }
            }
        }
    }

    // 4. Force every DEX `DeviceInfoProvider.evalBlobNative(...)` gate to true.
    //
    //    A whole class of features (SIM identity, IMEI, GAID, OAID, MediaDRM, BT MAC, virtual
    //    accounts, WiFi SSID/BSSID, LAN-scan-block) is coded but never places its hooks because
    //    the hook-registrar branch is gated on a DIRECT native `evalBlobNative(...)` call. Each
    //    gate is `invoke evalBlobNative; move-result vX; if-eqz vX, :skip`. Override the boolean
    //    result of every such call with a constant `true`, so the registrars place their hooks.
    fun isEvalBlob(instruction: Instruction): Boolean {
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            ?: return false
        return reference.definingClass ==
            "Lcom/wowsoftware/hidemyandroid/jni/DeviceInfoProvider;" &&
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
                val constInsn = if (register < 16) "const/4 v$register, 0x1"
                                else "const/16 v$register, 0x1"
                mutableMethod.replaceInstruction(index, constInsn)
            }
        }
    }

    // 5. Force the cached-licence-blob field non-empty so the Tier-2 gate passes.
    //
    //    The advanced-identifier registrars wrap their hook registration in
    //    `if (jVar.str.length() > 0) { if (evalBlobNative(jVar.str, ...) && ...) { ... } }`,
    //    where `jVar` is the per-version identity built in the framework hook manager. The
    //    blob field is pinned for this version (see [HmaPins]). With no licence the blob is
    //    empty, so the whole Tier-2 block is skipped. Forcing the field to a non-empty
    //    constant unlocks the whole tier; spoof values still come from the profile config.
    classDefForEach { classDef ->
        if (classDef.type != pins.blobClass) return@classDefForEach
        classDef.methods.filter { it.name == "<init>" }.forEach forEachCtor@{ method ->
            val impl = method.implementation ?: return@forEachCtor
            val index = impl.instructions.withIndex().firstOrNull { (_, insn) ->
                insn.opcode == Opcode.IPUT_OBJECT &&
                    ((insn as? ReferenceInstruction)?.reference as? FieldReference)?.let {
                        it.definingClass == pins.blobClass && it.name == "a"
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

/**
 * 1.7.1 (versionCode 171) — original pins.
 */
@Suppress("unused")
val unlockPremiumPatch171 = bytecodePatch(
    name = "Unlock premium",
    description = "Unlocks the premium feature set offline, without a subscription.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_HIDEMYANDROID_171)
    execute {
        applyHmaUnlock(
            pins = HmaPins(flagClass = "Lbb/g;", flagName = "c", blobClass = "Lwa/j;"),
            rewritePremiumFlow = true,
        )
    }
}

/**
 * 1.7.2 (versionCode 172) — re-derived pins (see `Constants.COMPATIBILITY_HIDEMYANDROID_172`
 * kdoc for the mapping). The premium-flow UI badge rewrite is skipped because the 1.7.2
 * emission pattern no longer matches the Boolean.FALSE adjacency heuristic; tier-2 hooks and
 * the LicenseStore flag still unlock.
 */
@Suppress("unused")
val unlockPremiumPatch172 = bytecodePatch(
    name = "Unlock premium",
    description = "Unlocks the premium feature set offline, without a subscription. " +
        "(1.7.2: full coverage — premium `MutableStateFlow` initial value forced TRUE; " +
        "runtime emissions are guarded by the LicenseStore flag rewritten in step 2, " +
        "so the flow stays TRUE.)",
    default = true,
) {
    compatibleWith(COMPATIBILITY_HIDEMYANDROID_172)
    execute {
        applyHmaUnlock(
            pins = HmaPins(
                flagClass = "Lcb/h;",
                flagName = "c",
                blobClass = "Lxa/k;",
                flowInitClass = "Le0/n1;",
            ),
            rewritePremiumFlow = false,
        )
    }
}
