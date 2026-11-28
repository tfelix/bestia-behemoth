package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class SwiftnessPotionScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 661L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.SWIFTNESS, 2)),
  statusEffects = statusEffects,
)
