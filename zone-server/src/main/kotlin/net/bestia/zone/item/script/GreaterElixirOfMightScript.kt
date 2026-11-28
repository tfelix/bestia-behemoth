package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class GreaterElixirOfMightScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 681L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.STRENGTH_ELIXIR, 2)),
  statusEffects = statusEffects,
)
