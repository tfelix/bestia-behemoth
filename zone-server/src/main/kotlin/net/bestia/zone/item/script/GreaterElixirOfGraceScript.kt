package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class GreaterElixirOfGraceScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 683L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.AGILITY_ELIXIR, 2)),
  statusEffects = statusEffects,
)
