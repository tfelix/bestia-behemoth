package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class ClarityDraughtScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 663L,
  isPotion = true,
  cures = setOf(StatusEffectId.BLIND, StatusEffectId.CONFUSED),
  statusEffects = statusEffects,
)
