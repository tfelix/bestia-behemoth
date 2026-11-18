package net.bestia.zone.capture

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class SuperTrapScript(
  outMessageProcessor: OutMessageProcessor
) : TrapScript(TrapTier.SUPER_TRAP, outMessageProcessor) {

  override val itemId = 617L
}
