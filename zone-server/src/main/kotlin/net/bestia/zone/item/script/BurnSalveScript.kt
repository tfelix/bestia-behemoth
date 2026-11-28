package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class BurnSalveScript(
  messages: OutMessageProcessor,
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 660L,
  health = 60,
  messages = messages,
  isPotion = true,
  cures = setOf(StatusEffectId.BURNING),
  statusEffects = statusEffects,
)
