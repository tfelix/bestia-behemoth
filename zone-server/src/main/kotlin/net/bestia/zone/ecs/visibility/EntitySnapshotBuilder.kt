package net.bestia.zone.ecs.visibility

import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.account.MasterVisual
import net.bestia.zone.ecs.core.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.dirtyableComponentTypes
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.spawn.townsfolk.TownsfolkVisual
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * Everything one account has to be told for an entity it has just been able to see, as the ordinary
 * per-component messages.
 *
 * Built from [dirtyableComponentTypes] rather than a hand-written list of builders, so a new syncable
 * component is in a snapshot the moment it exists.
 */
@Component
class EntitySnapshotBuilder {

  /**
   * Component types whose message has to arrive before the others, in this order; the rest follow in whatever
   * order the scan produced.
   *
   * `entity.gd` needs two of these ordered: a visual first, because anything routed through
   * `_get_visual_for_method` is dropped with an error while the node has no visual child; and [Position]
   * before [Path], because `update_path` anchors its waypoint chain on where the entity currently is, which
   * on a node created this frame is the world origin.
   */
  private val sendFirst: List<KClass<*>> = listOf(
    EntityVisual::class,
    MasterVisual::class,
    TownsfolkVisual::class,
    Position::class,
    Speed::class,
    Path::class
  )

  private val orderedTypes = dirtyableComponentTypes.sortedBy { type ->
    sendFirst.indexOf(type).let { if (it < 0) sendFirst.size else it }
  }

  /**
   * @param accountId who is being told. Decides which components are included: this is the whole of the
   *   filtering, so a snapshot cannot leak another player's inventory.
   */
  fun build(world: World, entityId: EntityId, accountId: AccountId): List<EntitySMSG> =
    orderedTypes.mapNotNull { type ->
      val component = world.get(entityId, type) as? Dirtyable ?: return@mapNotNull null

      if (!isVisibleTo(world, entityId, accountId, component)) return@mapNotNull null

      component.toEntityMessage(entityId)
    }

  /**
   * [SyncTargets.OwnerOnly] is excluded even for the owner's own entity.
   *
   * The observer holds their own chunk from login onwards and re-holds it after every teleport, so an
   * owner-only component here would be re-delivered on each of those - and a re-delivered `LogoutIntent`
   * restarts the client's logout countdown. `GetSelfHandler.resyncOwnerComponents` owns that channel.
   */
  private fun isVisibleTo(
    world: World,
    entityId: EntityId,
    accountId: AccountId,
    component: Dirtyable
  ): Boolean = when (val targets = component.syncTargets(world, entityId)) {
    is SyncTargets.PublicInRange -> true
    is SyncTargets.Accounts -> accountId in targets.accountIds
    is SyncTargets.OwnerOnly -> false
  }
}
