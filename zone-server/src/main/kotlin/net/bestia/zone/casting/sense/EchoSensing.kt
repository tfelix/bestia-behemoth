package net.bestia.zone.casting.sense

import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component

/** Echo Sounding: the hollows around the caster, exact, with how deep and how large once the skill tells. */
@Component
class EchoSensing(
  private val hollows: HollowFinder,
  private val worldService: WorldService,
  private val messages: OutMessageProcessor,
) {

  fun sound(world: World, casterId: EntityId, radiusMetres: Int, revealsDepth: Boolean) {
    val accountId = world.get(casterId, Account::class)?.accountId ?: return
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return
    val voxel = worldService.config.voxelSize
    val radius = (radiusMetres / voxel).toInt()

    val marks = hollows.around(at, radius).map { hollow ->
      SenseMark(
        kind = SenseKind.HOLLOW,
        eastMetres = hollow.centre.x * voxel,
        northMetres = hollow.centre.y * voxel,
        depthMetres = if (revealsDepth) hollow.depth * voxel else 0.0,
        sizeMetres = if (revealsDepth) hollow.size * voxel else 0.0,
      )
    }

    messages.sendToPlayer(accountId, SenseMarksSMSG(marks, VISIBLE_SECONDS))
  }

  private companion object {
    /** As long as the skill's cooldown, so a prospector knocking their way along always has the last echo in view. */
    const val VISIBLE_SECONDS = 20
  }
}
