package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class ElixirOfFortitudeScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 665L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.VITALITY_ELIXIR, 1)),
  statusEffects = statusEffects,
)
