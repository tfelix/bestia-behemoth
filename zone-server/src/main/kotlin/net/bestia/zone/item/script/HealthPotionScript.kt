package net.bestia.zone.item.script

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class HealthPotionScript(
  messages: OutMessageProcessor,
) : RestorativeScript(itemId = 620L, health = 170, messages = messages, isPotion = true)
