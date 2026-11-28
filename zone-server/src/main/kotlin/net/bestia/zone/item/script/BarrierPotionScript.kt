package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class BarrierPotionScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 677L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.BARRIER, 5)),
  statusEffects = statusEffects,
)
