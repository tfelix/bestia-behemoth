package net.bestia.zone.item.script

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class NoviceHealthPotionScript(
  messages: OutMessageProcessor,
) : RestorativeScript(itemId = 69L, health = 50, messages = messages)
