package net.bestia.zone.item.script

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class GreaterHealthPotionScript(
  messages: OutMessageProcessor,
) : RestorativeScript(itemId = 669L, health = 300, messages = messages, isPotion = true)
