package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class ElixirOfGraceScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 666L,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.AGILITY_ELIXIR, 1)),
  statusEffects = statusEffects,
)
