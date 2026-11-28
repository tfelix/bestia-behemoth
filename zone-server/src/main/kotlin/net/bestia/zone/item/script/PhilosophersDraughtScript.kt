package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class PhilosophersDraughtScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 692L,
  grants = listOf(
    RestorativeScript.Grant(StatusEffectId.STRENGTH_ELIXIR, 3),
    RestorativeScript.Grant(StatusEffectId.VITALITY_ELIXIR, 3),
    RestorativeScript.Grant(StatusEffectId.INTELLIGENCE_ELIXIR, 3),
    RestorativeScript.Grant(StatusEffectId.DEXTERITY_ELIXIR, 3),
    RestorativeScript.Grant(StatusEffectId.AGILITY_ELIXIR, 3),
    RestorativeScript.Grant(StatusEffectId.WILLPOWER_ELIXIR, 3),
  ),
  statusEffects = statusEffects,
)
