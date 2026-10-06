package net.bestia.zone.prop

import net.bestia.zone.battle.CombatTargetPromotion
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Grounded
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.PropVitality
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Turns a static prop into an ordinary, attackable entity the first time something targets or damages it -
 * the mechanism `PropPose`'s own KDoc has described since it was written, made real.
 *
 * ### Where this has to be called from, and why it is not enough to call it from just one place
 *
 * `BattleContextFactory.battleEntity` is where every real damage-resolving path converges, so it calls this
 * defensively - but calling it *only* there is not safe for a channelled (cast-time > 0) skill.
 * `CastingSystem.update` resolves a completed channel from **inside** a running `System.update()`, i.e. with
 * the world-global `iterating` flag true, and `World.add` defers under that flag until `applyDeferred()` runs
 * at the very end of that tick's systems phase. Promoting and then immediately reading the same components
 * back in the same call - which is exactly what `battleEntity` does - would see nothing yet, and the very
 * first hit of any channelled skill against a pristine prop would silently fizzle.
 *
 * So `ActivateSkillHandler` also calls this, right after resolving a target id, before branching on cast
 * time - that call happens from a message-handler context (`world.modify`), never nested inside
 * `scheduler.tick()`, so the `add`s below apply immediately and a channelled cast that resolves seconds
 * later finds the prop already promoted. The defensive call in `BattleContextFactory` remains for any other
 * caller reached the same, non-nested way (today, everything that names a target does).
 */
@Component
class PropPromotionService(
  private val divergence: WorldObjectDivergenceRegistry,
) : CombatTargetPromotion {

  /**
   * Idempotent: an already-promoted entity (or an entity that was never a prop at all) is a cheap no-op.
   *
   * A prop further than [reach] from [from] - where its actor stands - stays a prop. Its entity id is all a client
   * has to send, and each promotion makes a full entity, so without this one player could promote every prop in
   * the world.
   *
   * @return true if [entityId] is now (or already was) a combat-capable entity; false if it is a static prop
   *   that refuses promotion because it is out of reach, or its divergence is terminal (a claimed POI, a
   *   mined-out crystal) or not yet regrown - in which case the caller proceeds exactly as it does for any
   *   other unresolvable target.
   */
  override fun promoteIfNeeded(world: World, entityId: EntityId, from: Vec3L, reach: Long): Boolean {
    if (world.has(entityId, Position::class)) return true

    val identity = world.get(entityId, WorldObjectIdentity::class) ?: return true
    if (divergence.of(identity.propId) != null) return false

    val pose = world.get(entityId, PropPose::class) ?: return false
    val vitality = world.get(entityId, PropVitality::class) ?: return false
    if (pose.position.distance(from) > reach) return false

    world.add(entityId, Position.fromVec3(pose.position))
    world.add(entityId, Grounded)
    world.add(entityId, Health(current = vitality.maxHp, max = vitality.maxHp))
    // Low but non-zero: PhysicalSkillStrategy divides the attacker's DEX/AGI by the defender's, so a defender
    // stat of exactly 0 degenerates that ratio to Infinity (clamped, not a crash, but not the intent either).
    // Shaped right rather than balanced, the same disclaimer prop-kinds.yml already carries for max-hp.
    world.add(
      entityId,
      StatusValues(strength = BASELINE_STAT, intelligence = BASELINE_STAT, vitality = BASELINE_STAT,
        dexterity = BASELINE_STAT, willpower = BASELINE_STAT, agility = BASELINE_STAT)
    )

    return true
  }

  companion object {
    private const val BASELINE_STAT = 1
  }
}
