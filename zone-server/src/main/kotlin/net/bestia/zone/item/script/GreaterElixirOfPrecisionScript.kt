package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class GreaterElixirOfPrecisionScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 680L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.DEXTERITY_ELIXIR, 2)),
  statusEffects = statusEffects,
)
