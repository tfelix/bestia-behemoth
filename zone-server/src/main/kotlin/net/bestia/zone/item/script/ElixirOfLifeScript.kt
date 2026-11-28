package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class ElixirOfLifeScript(
  messages: OutMessageProcessor,
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 691L,
  health = 700,
  stamina = 200,
  mana = 400,
  messages = messages,
  isPotion = true,
  cures = setOf(StatusEffectId.POISON, StatusEffectId.BLEEDING),
  statusEffects = statusEffects,
)
