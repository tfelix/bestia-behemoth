package net.bestia.zone.teleport

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityFx
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Teleporting
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.travel.Journey
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Stepping into a warp portal: whoever clicks it, within reach, comes out at the other end while passages last. */
@Component
class WarpPortalInteraction(
  private val settings: TeleportItemSettings,
) : PropInteraction {

  override val kind = StaticEntityKind.WARP_PORTAL

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    val end = world.get(propId, WarpPortalEnd::class) ?: return OpError.PORTAL_SPENT
    if (end.passages.left <= 0) return OpError.PORTAL_SPENT
    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return null
    val portalAt = world.get(propId, Position::class)?.toVec3L() ?: return OpError.PORTAL_SPENT
    if (actorAt.distance(portalAt) > REACH_TILES) return OpError.TELEPORT_NOT_NOW
    if (world.has(actorId, Teleporting::class)) return null

    end.passages.left -= 1
    world.remove(actorId, Path::class)
    world.remove(actorId, Journey::class)
    world.add(actorId, Teleporting(Teleporting.Destination.Spot(end.exit), secondsUntilMove = settings.flyWingVanishMs / 1000f))
    world.add(actorId, EntityFx(EntityFx.Kind.WING_VANISH, settings.flyWingVanishMs))

    return null
  }

  private companion object {
    const val REACH_TILES = 3L
  }
}
