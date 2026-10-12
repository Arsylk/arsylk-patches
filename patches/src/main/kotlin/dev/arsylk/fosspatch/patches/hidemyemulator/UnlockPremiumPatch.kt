package dev.arsylk.fosspatch.patches.hidemyemulator

import dev.arsylk.fosspatch.patches.shared.Constants.COMPATIBILITY_HIDEMYEMULATOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Restores the premium feature set of Hide My Emulator (all device masking) without the paid
 * `com.wowsoftware.hmelicense` licence, and works fully offline. The app shows as licensed and the
 * protections apply.
 *
 * Two coordinated bytecode changes:
 *
 *  1. The app-wide licence state reducer ([LicenseStateReducerFingerprint]) is forced to return the
 *     app's own "licensed" model. The licence repository pushes that model into the StateFlow the UI
 *     and the protection toggles observe, so the UI reads as activated and the protections unlock.
 *     The model and its constructor are read from the matched method (so no obfuscated name is
 *     hard-coded); it is constructed with null arguments, which the app treats as a licence with no
 *     expiry (the exact "lifetime" model it already produces internally).
 *
 *  2. The mask builder's expiry gate ([LicenseGateFingerprint]) has its `if-lez` removed, so the
 *     masks apply in hooked processes regardless of the cached expiry (which is never written
 *     offline). Operand- and register-agnostic.
 *
 * Only the DEX is modified.
 */
@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock premium",
    description = "Unlocks all device masking offline, without the paid licence app.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_HIDEMYEMULATOR)

    execute {
        // 1. Force the licence state reducer to return a licensed model (app-wide activated state).
        val reducer = LicenseStateReducerFingerprint.method
        val instructions = reducer.implementation!!.instructions.toList()

        // The licensed model is the (only) class instantiated here; the unlicensed one is a singleton.
        val newInstanceIndex = instructions.indexOfFirst { it.opcode == Opcode.NEW_INSTANCE }
        val modelType = (instructions[newInstanceIndex] as ReferenceInstruction).reference.toString()
        val constructor = instructions.drop(newInstanceIndex)
            .first { it.opcode == Opcode.INVOKE_DIRECT }
            .let { (it as ReferenceInstruction).reference as MethodReference }
        val constructorRef =
            "${constructor.definingClass}->${constructor.name}(" +
                constructor.parameterTypes.joinToString("") + ")V"
        // this + one null per constructor parameter (all are object references).
        val argRegisters = (listOf("v1") + List(constructor.parameterTypes.size) { "v0" }).joinToString(", ")

        reducer.addInstructions(
            0,
            """
                const/4 v0, 0x0
                new-instance v1, $modelType
                invoke-direct {$argRegisters}, $constructorRef
                return-object v1
            """,
        )

        // 2. Drop the mask builder's expiry `if-lez`, so the masks always run in hooked processes.
        val gate = LicenseGateFingerprint
        gate.method.removeInstruction(gate.instructionMatches.last().index)
    }
}
