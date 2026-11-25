package net.bestia.zone.inn

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Clicking a player-built inn works like its innkeeper's counter, while its town stands. */
@Component
class InnInteraction(
  private val inns: PlayerInns,
  private val inn: InnService,
) : PropInteraction {

  override val kind = StaticEntityKind.INN

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    if (!inns.isInReach(world, actorId, propId)) return OpError.TALK_OUT_OF_RANGE
    val counter = inns.counterOf(world, propId) ?: return OpError.INN_CLOSED
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null

    inn.open(accountId, counter)

    return null
  }
}
