package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class WarmingDraughtScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 659L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.WARMED, 1)),
  statusEffects = statusEffects,
)
