package net.bestia.zone.item.script

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class SlimSupremeHealthPotionScript(
  messages: OutMessageProcessor,
) : RestorativeScript(itemId = 689L, health = 650, messages = messages, isPotion = true)
