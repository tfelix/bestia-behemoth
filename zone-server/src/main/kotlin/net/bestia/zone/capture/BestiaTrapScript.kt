package net.bestia.zone.capture

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

/** The Bestia Trap (`items.yml` id 68): the entry-level trap, good against young bestia only. */
@Component
class BestiaTrapScript(
  outMessageProcessor: OutMessageProcessor
) : TrapScript(TrapTier.BESTIA_TRAP, outMessageProcessor) {

  override val itemId = 68L
}
