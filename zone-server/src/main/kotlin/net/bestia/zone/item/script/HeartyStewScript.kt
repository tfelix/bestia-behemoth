package net.bestia.zone.item.script

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

/** What Cooking produces, and the only thing a player can make that restores both. */
@Component
class HeartyStewScript(
  messages: OutMessageProcessor,
) : RestorativeScript(itemId = 18L, health = 60, stamina = 40, messages = messages)
