package net.bestia.zone.postal.office

import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PropFootprint
import net.bestia.zone.townsfolk.conversation.TalkService
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component

/**
 * The counter a post office request names, re-checked on every request because a client can name any entity:
 * a postmaster in talking range, or a player-built office the actor stands at.
 */
@Component
class PostCounters(
  private val talk: TalkService,
  private val postmasters: PostmasterCounters,
  private val offices: PostOffices,
  private val worldService: WorldService,
) {

  fun inReach(world: World, accountId: AccountId, actor: EntityId, target: EntityId): PostCounter? {
    if (world.get(target, StaticVisual::class)?.kind == StaticEntityKind.POST_OFFICE) {
      return playerOfficeInReach(world, actor, target)
    }

    return talk.speakerInReach(accountId, actor, target)?.let(postmasters::counterOf)
  }

  /** The building stands where the post runs it, so it is open while its town stands. */
  fun playerOfficeInReach(world: World, actor: EntityId, building: EntityId): PostCounter? {
    val visual = world.get(building, StaticVisual::class) ?: return null
    val pose = world.get(building, PropPose::class) ?: return null
    val actorAt = world.get(actor, Position::class)?.toVec3L() ?: return null
    if (PropFootprint.distanceTo(actorAt, pose, visual, worldService.config.voxelSize) > REACH_TILES) return null

    return PostCounter(building, offices.playerOfficeAt(pose.position))
  }

  private companion object {
    /** As at an auction house: the client stops two tiles out, and one more absorbs latency. */
    const val REACH_TILES = 3.0
  }
}
