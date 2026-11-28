package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class GreaterElixirOfResolveScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 688L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.WILLPOWER_ELIXIR, 2)),
  statusEffects = statusEffects,
)
