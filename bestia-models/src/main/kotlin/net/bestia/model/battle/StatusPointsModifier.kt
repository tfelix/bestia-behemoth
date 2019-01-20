package net.bestia.model.battle

import java.io.Serializable

data class StatusPointsModifier(
    var strengthMod: Float = 1f,
    var strengthValue: Float = 0f,

    var vitalityMod: Float = 1f,
    var vitalityValue: Float = 0f,

    var intelligenceMod: Float = 1f,
    var intelligenceValue: Float = 0f,

    var agilityMod: Float = 1f,
    var agilityValue: Float = 0f,

    var willpowerMod: Float = 1f,
    var willpowerValue: Float = 0f,

    var dexterityMod: Float = 1f,
    var dexterityValue: Float = 0f,

    var defenseMod: Float = 1f,
    var defenseValue: Float = 0f,

    var magicDefenseMod: Float = 1f,
    var magicDefenseValue: Float = 0f,

    var maxHpMod: Float = 1f,
    var maxHpValue: Float = 0f,

    var maxManaMod: Float = 1f,
    var maxManaValue: Float = 0f
) : Serializable
