package net.bestia.zone.item.script

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class AppleScript(
  messages: OutMessageProcessor,
) : RestorativeScript(itemId = 1L, health = 25, messages = messages)
