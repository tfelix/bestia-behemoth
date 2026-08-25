package net.bestia.zone.world.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.item.ObtainItemIntent
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.prop.CollectPropIntent
import net.bestia.zone.ecs.prop.PropPose
import net.bestia.zone.ecs.prop.StaticVisual
import net.bestia.zone.ecs.prop.WorldObjectIdentity
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent
import java.time.Instant

/**
 * Resolves a [CollectPropIntent]: claims the prop, grants its item, and takes it out of the world.
 *
 * The second of the two ways a static entity is used up. The other is being killed - `PropPromotionService`
 * turns a prop into an ordinary combat entity on the first point of damage and [PropDeathDivergenceSystem]
 * decides what its death meant. Both end in the same place, `WorldObjectDivergenceRegistry.recordDepletion`,
 * and that is what stops them ever both claiming one prop.
 *
 * ### Why the claim works, in one paragraph
 *
 * `World.tick` holds the world lock across the whole tick *including* `applyDeferred`, and a message handler
 * reaches the ECS only through `world.modify` on that same lock. So every intent attached since the last tick
 * is already applied when this runs, and none can land mid-iteration. Two players who clicked the same
 * crystal are therefore both visited by one pass of this loop: the first passes the divergence check and
 * `recordDepletion` writes the in-memory map **synchronously**, so the second sees it and is refused.
 *
 * Note what would *not* have worked. `world.destroy` and `world.add` both defer while `iterating` is set, so
 * for the second player the prop is still alive and still unmarked - a liveness test or a claim marker
 * component would have granted the item twice. The divergence map is the only state here that changes the
 * instant it is written.
 *
 * ### @Order(64)
 *
 * Before [PropDeathDivergenceSystem] at 65, so a prop collected and felled in the same tick yields once -
 * that system carries the matching guard. Before `DeathSystem` (@70) and `PersistAndRemoveSystem` (@90),
 * because `World.addNow` requires its target alive and the deferred queue is FIFO: running later would let a
 * player who dies in the same tick they collect blow up `applyDeferred` on the tick thread.
 *
 * ### One tick of latency, deliberately
 *
 * The [ObtainItemIntent.CreateItemIntent] added below is deferred like every other add, and
 * `ObtainItemIntentSystem` (@59) has already run. So the item lands on the next tick - 50 ms, invisible, and
 * the same shape `LootItemHandler` already has. `CreateItemIntent` is worth that: it brings the carry-weight
 * check, the async persistence, the automatic `InventoryComponentSMSG`, and - the reason it is not worth
 * hand-rolling - the drop-at-your-feet fallback when the item does not fit. A collected crystal can never be
 * destroyed without yielding something, which is why there is no "inventory full" refusal.
 */
@SpringComponent
@Order(64)
class CollectPropIntentSystem(
  private val kinds: PropKindRegistry,
  private val divergence: WorldObjectDivergenceRegistry,
  private val residency: WorldObjectResidencyService,
  private val outMessageProcessor: OutMessageProcessor,
) : System {

  override val reads: ComponentClassSet = setOf(
    Account::class, Position::class, PropPose::class, StaticVisual::class, WorldObjectIdentity::class
  )

  /**
   * [ObtainItemIntent.CreateItemIntent] is declared even though it is added to a *different* entity than the
   * one this system queries - it is what keeps this out of the same scheduler wave as `ObtainItemIntentSystem`.
   */
  override val writes: ComponentClassSet = setOf(
    CollectPropIntent::class, ObtainItemIntent.CreateItemIntent::class
  )

  override fun update(world: World, deltaTime: Float) {
    world.query(CollectPropIntent::class).each { collectorId ->
      val intent = get<CollectPropIntent>()
      collect(world, collectorId, intent.propEntityId)
      world.remove(collectorId, CollectPropIntent::class)
    }
  }

  private fun collect(world: World, collectorId: EntityId, propId: EntityId) {
    // Any of these missing means the id names something that is not a live prop: a stale handle from a column
    // that was re-materialised, one already collected or felled this tick, or simply not a prop at all. The
    // client cannot tell those apart, and should not be able to - see COLLECT_TARGET_GONE.
    val pose = world.get(propId, PropPose::class)
    val visual = world.get(propId, StaticVisual::class)
    val identity = world.get(propId, WorldObjectIdentity::class)

    if (pose == null || visual == null || identity == null) {
      return deny(world, collectorId, OperationErrorProto.OpError.COLLECT_TARGET_GONE)
    }

    val spec = kinds.of(visual.kind)
    val collect = spec.collect
      ?: return deny(world, collectorId, OperationErrorProto.OpError.COLLECT_NOT_COLLECTIBLE)

    if (divergence.of(identity.propId) != null) {
      return deny(world, collectorId, OperationErrorProto.OpError.COLLECT_TARGET_GONE)
    }

    // Read off PropPose, not Position. A pristine prop has no Position at all - that is PropPose's whole
    // reason to exist - and promotion adds one without removing the pose. Reading Position would make a
    // crystal collectible only after somebody had already hit it.
    val collectorPosition = world.get(collectorId, Position::class)?.toVec3L()
      ?: return deny(world, collectorId, OperationErrorProto.OpError.COLLECT_TARGET_GONE)

    if (collectorPosition.distance(pose.position) > MAX_COLLECT_RANGE) {
      return deny(world, collectorId, OperationErrorProto.OpError.COLLECT_OUT_OF_RANGE)
    }

    // The claim. Synchronous in memory, which is the whole reason the race above resolves - see the class note.
    // resumeAt is derived rather than hardcoded to null: every collectible kind is terminal today, and a
    // future one that regrows should need no change here.
    val resumeAt = spec.regrowSeconds?.let { Instant.now().plusSeconds(it) }
    divergence.recordDepletion(identity.propId, visual.kind, resumeAt)

    residency.remove(world, propId)
    world.add(collectorId, ObtainItemIntent.CreateItemIntent(collect.itemId, collect.amount))

    LOG.debug { "Entity $collectorId collected ${visual.kind} (prop ${identity.propId})" }
  }

  private fun deny(world: World, collectorId: EntityId, code: OperationErrorProto.OpError) {
    val accountId = world.get(collectorId, Account::class)?.accountId ?: return
    outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(code))
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /**
     * File-private, and looser than `ObtainItemIntentSystem`'s own `MAX_LOOT_RANGE = 1`, because the two mean
     * different things: a dropped stack lies at your feet, and a crystal is a metre-wide object you stand
     * beside. Two numbers that happen to be lengths are not a shared constant.
     *
     * `Vec3L.distance` is horizontal only and truncates, so this errs lenient and ignores height - consistent
     * with every other range check in the codebase rather than diverging here.
     */
    const val MAX_COLLECT_RANGE = 3L
  }
}
