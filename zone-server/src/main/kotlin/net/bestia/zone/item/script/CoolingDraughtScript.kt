package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class CoolingDraughtScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 674L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.COOLED, 1)),
  statusEffects = statusEffects,
)
