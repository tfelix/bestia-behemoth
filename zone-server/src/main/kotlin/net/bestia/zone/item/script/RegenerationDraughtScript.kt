package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class RegenerationDraughtScript(
  messages: OutMessageProcessor,
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 662L,
  health = 100,
  messages = messages,
  isPotion = true,
  grants = listOf(RestorativeScript.Grant(StatusEffectId.REGENERATING, 1)),
  statusEffects = statusEffects,
)
