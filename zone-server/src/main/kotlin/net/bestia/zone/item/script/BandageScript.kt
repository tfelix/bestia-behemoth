package net.bestia.zone.item.script

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

/** The cheapest heal there is, and the one a player can afford before their first potion. */
@Component
class BandageScript(
  messages: OutMessageProcessor,
) : RestorativeScript(itemId = 34L, health = 20, messages = messages)
