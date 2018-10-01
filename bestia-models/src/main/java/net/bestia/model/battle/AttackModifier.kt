package net.bestia.model.battle

/**
 * The [AttackModifier] is used to numerically alter attack parameters.
 * Modifier. The come in two variations:
 *
 * *Mod: This multiplies with the attack value. *Value: This adds or
 * subtracts from the attack value.
 *
 * @author Thomas Felix
 */
data class AttackModifier(
    var strengthMod: Float = 1f,
    var strengthValue: Float = 0f,
    var manaCostMod: Float = 1f,
    var manaCostValue: Float = 0f,
    var rangeMod: Float = 1f,
    var rangeValue: Float = 0f,
    var cooldownMod: Float = 1f,
    var cooldownValue: Float = 0f
)
