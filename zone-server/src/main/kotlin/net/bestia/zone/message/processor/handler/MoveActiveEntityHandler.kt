package net.bestia.zone.message.processor.handler

import com.github.quillraven.fleks.World
import net.bestia.zone.message.MoveActiveEntityCMSG
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.session.ActiveEntityResolver
import org.springframework.stereotype.Component

/**
 * This moves a player owned entity the given path.
 */
@Component
class MoveActiveEntityHandler(
  private val activeEntityResolver: ActiveEntityResolver
) : IncomingEcsMessageHandler<MoveActiveEntityCMSG>()  {
  override val handles = MoveActiveEntityCMSG::class

  override fun process(world: World, msg: MoveActiveEntityCMSG) {
    val entity = activeEntityResolver.findActiveEntityByAccountIdOrThrow(msg.playerId)

    with(world) {
      entity.configure {
        it += Path(msg.path.toMutableList())
      }
    }
  }
}