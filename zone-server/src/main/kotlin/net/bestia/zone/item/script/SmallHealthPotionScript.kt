package net.bestia.zone.item.script

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class SmallHealthPotionScript(
  messages: OutMessageProcessor,
) : RestorativeScript(itemId = 3L, health = 45, messages = messages)
