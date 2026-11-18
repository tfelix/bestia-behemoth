package net.bestia.zone.capture

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class MasterTrapScript(
  outMessageProcessor: OutMessageProcessor
) : TrapScript(TrapTier.MASTER_TRAP, outMessageProcessor) {

  override val itemId = 619L
}
