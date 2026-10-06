package net.bestia.zone.engine

import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.ecs.account.MasterVisual
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.sync.dirtyableComponentTypes
import net.bestia.zone.entity.ecs.EntityVisual
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
  fun build(world: World, entityId: EntityId, accountId: AccountId): List<EntitySMSG> {
    return snapshotOf(world, entityId).visibleTo(accountId)
  }

  /**
   * Every message an entity's snapshot can contain, with who may see each. Built once and filtered per
   * viewer, because a crowd arriving in one chunk is seen by everyone who holds it.
   *
   * [SyncTargets.OwnerOnly] is left out even for the owner's own entity. The observer holds their own chunk
   * from login onwards and re-holds it after every teleport, so an owner-only component here would be
   * re-delivered on each of those - and a re-delivered `LogoutIntent` restarts the client's logout
   * countdown. `GetSelfHandler.resyncOwnerComponents` owns that channel.
   */
  fun snapshotOf(world: World, entityId: EntityId): Snapshot {
    val entries = orderedTypes.mapNotNull { type ->
      val component = world.get(entityId, type) as? Dirtyable ?: return@mapNotNull null

      val targets = component.syncTargets(world, entityId)
      if (targets is SyncTargets.OwnerOnly) return@mapNotNull null

      targets to component.toEntityMessage(entityId)
    }
    return Snapshot(entries)
  }

  class Snapshot internal constructor(private val entries: List<Pair<SyncTargets, EntitySMSG>>) {

    fun visibleTo(accountId: AccountId): List<EntitySMSG> {
      return entries.mapNotNull { (targets, message) ->
        val visible = when (targets) {
          is SyncTargets.PublicInRange -> true
          is SyncTargets.Accounts -> accountId in targets.accountIds
          is SyncTargets.OwnerOnly -> false
        }
        if (visible) message else null
      }
    }
  }
}
