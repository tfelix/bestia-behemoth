package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class GreaterElixirOfFortitudeScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 687L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.VITALITY_ELIXIR, 2)),
  statusEffects = statusEffects,
)
