package net.bestia.zone.postal.office

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Clicking a player-built post office opens its window: the post runs it, so no clerk stands behind a counter. */
@Component
class PostOfficeInteraction(
  private val counters: PostCounters,
  private val desk: PostOfficeDesk,
) : PropInteraction {

  override val kind = StaticEntityKind.POST_OFFICE

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    val counter = counters.playerOfficeInReach(world, actorId, propId) ?: return OpError.TALK_OUT_OF_RANGE
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null

    return if (desk.open(accountId, counter)) null else OpError.POST_OFFICE_CLOSED
  }
}
