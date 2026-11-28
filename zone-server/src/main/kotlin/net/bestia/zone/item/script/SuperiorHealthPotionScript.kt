package net.bestia.zone.item.script

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class SuperiorHealthPotionScript(
  messages: OutMessageProcessor,
) : RestorativeScript(itemId = 678L, health = 480, messages = messages, isPotion = true)
