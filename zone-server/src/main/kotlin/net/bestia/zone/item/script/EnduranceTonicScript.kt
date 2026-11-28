package net.bestia.zone.item.script

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class EnduranceTonicScript(
  messages: OutMessageProcessor,
) : RestorativeScript(itemId = 673L, health = 100, stamina = 300, messages = messages, isPotion = true)
