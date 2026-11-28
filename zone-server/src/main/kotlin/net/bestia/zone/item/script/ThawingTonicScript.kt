package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class ThawingTonicScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 672L,
  isPotion = true,
  cures = setOf(StatusEffectId.FROZEN, StatusEffectId.SLOWED),
  grants = listOf(RestorativeScript.Grant(StatusEffectId.WARMED, 1)),
  statusEffects = statusEffects,
)
