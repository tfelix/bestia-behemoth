package net.bestia.zone.item.script

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class RejuvenationPotionScript(
  messages: OutMessageProcessor,
) : RestorativeScript(itemId = 675L, health = 300, mana = 300, messages = messages, isPotion = true)
