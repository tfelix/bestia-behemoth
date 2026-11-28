package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class ElixirOfResolveScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 671L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.WILLPOWER_ELIXIR, 1)),
  statusEffects = statusEffects,
)
