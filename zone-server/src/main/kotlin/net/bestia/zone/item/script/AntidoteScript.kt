package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class AntidoteScript(
  messages: OutMessageProcessor,
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 657L,
  health = 30,
  messages = messages,
  isPotion = true,
  cures = setOf(StatusEffectId.POISON),
  statusEffects = statusEffects,
)
