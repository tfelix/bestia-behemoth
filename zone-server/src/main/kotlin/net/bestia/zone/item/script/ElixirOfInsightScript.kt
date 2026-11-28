package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class ElixirOfInsightScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 668L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.INTELLIGENCE_ELIXIR, 1)),
  statusEffects = statusEffects,
)
