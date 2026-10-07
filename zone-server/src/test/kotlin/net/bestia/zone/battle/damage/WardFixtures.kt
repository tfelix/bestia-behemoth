package net.bestia.zone.battle.damage

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.util.EntityId

fun World.ownByPlayer(id: EntityId) {
  add(id, Account(accountId = id))
}

/** As WARD_AURA leaves a player standing near a ward stone. */
fun World.wardPlayer(id: EntityId) {
  ownByPlayer(id)
  val warded = ActiveStatusEffect(StatusEffectId.WARDED.id, level = 1, remainingSeconds = 5f, shield = HarmShield.PLAYERS)
  add(id, StatusEffects(mutableListOf(warded)))
}
