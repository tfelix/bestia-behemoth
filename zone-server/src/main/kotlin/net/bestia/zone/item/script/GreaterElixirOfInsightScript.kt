package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class GreaterElixirOfInsightScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 684L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.INTELLIGENCE_ELIXIR, 2)),
  statusEffects = statusEffects,
)
