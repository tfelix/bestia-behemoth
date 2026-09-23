package net.bestia.zone.item.script

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

/**
 * More than a potion, and an apothecary rather than a shelf: this is what a town with one is worth
 * walking to.
 */
@Component
class MedicineScript(
  messages: OutMessageProcessor,
) : RestorativeScript(itemId = 62L, health = 70, stamina = 30, messages = messages)
