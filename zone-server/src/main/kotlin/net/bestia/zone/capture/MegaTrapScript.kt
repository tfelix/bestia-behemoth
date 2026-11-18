package net.bestia.zone.capture

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class MegaTrapScript(
  outMessageProcessor: OutMessageProcessor
) : TrapScript(TrapTier.MEGA_TRAP, outMessageProcessor) {

  override val itemId = 618L
}
