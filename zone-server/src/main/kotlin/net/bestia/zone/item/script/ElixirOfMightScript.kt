package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class ElixirOfMightScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 664L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.STRENGTH_ELIXIR, 1)),
  statusEffects = statusEffects,
)
