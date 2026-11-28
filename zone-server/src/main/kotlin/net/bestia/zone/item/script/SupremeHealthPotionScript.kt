package net.bestia.zone.item.script

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class SupremeHealthPotionScript(
  messages: OutMessageProcessor,
) : RestorativeScript(itemId = 685L, health = 650, messages = messages, isPotion = true)
