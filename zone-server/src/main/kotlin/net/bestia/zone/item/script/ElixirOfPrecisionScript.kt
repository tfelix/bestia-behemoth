package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class ElixirOfPrecisionScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 667L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.DEXTERITY_ELIXIR, 1)),
  statusEffects = statusEffects,
)
