package net.bestia.zone.socket

import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import org.springframework.stereotype.Component

@Component
class PingHandler(
  private val outMessageProcessor: OutMessageProcessor
) : TickMessageHandler<PingCMSG> {
  override val handles = PingCMSG::class

  override fun handle(world: World, msg: PingCMSG): Boolean {
    outMessageProcessor.sendToPlayer(msg.playerId, PongSMSG)

    return true
  }
}