package net.bestia.zoneserver.battle

import net.bestia.model.battle.Attack

data class LearnedAttack(
    val minLevel: Int,
    val attack: Attack
)