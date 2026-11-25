package net.bestia.zone.inn

import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.townsfolk.conversation.TalkService
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The inn counter a request names, re-checked on every request because a client can name any entity: an innkeeper in
 * talking range, or a player-built inn the actor stands at.
 */
@Component
class InnCounters(
  private val talk: TalkService,
  private val keepers: InnKeepers,
  private val playerInns: PlayerInns,
) {

  fun inReach(world: World, accountId: AccountId, actor: EntityId, target: EntityId): InnCounter? {
    if (world.get(target, StaticVisual::class)?.kind == StaticEntityKind.INN) {
      return if (playerInns.isInReach(world, actor, target)) playerInns.counterOf(world, target) else null
    }

    return talk.speakerInReach(accountId, actor, target)?.let(keepers::counterOf)
  }
}
