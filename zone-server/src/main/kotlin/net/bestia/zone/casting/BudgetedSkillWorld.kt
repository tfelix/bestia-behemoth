package net.bestia.zone.casting

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Invulnerable
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.spoor.TrackReading
import net.bestia.zone.battle.ecs.damage.IncomingDamage
/**
 * The real [SkillWorld]: every operation is charged against the cast's [SkillBudget]. A cast resolves on the
 * tick thread (see [SkillExecutionService]), so it uses the [World] directly.
 *
 * One instance per cast, because the budget is.
 */
class BudgetedSkillWorld(
  private val world: World,
  private val budget: SkillBudget,
  private val services: SkillWorldServices,
  private val casterId: EntityId,
  private val skillId: Long,
  private val skillLevel: Int,
) : SkillWorld {

  override val remainingOps: Int get() = budget.remainingOps

  override fun isAlive(entityId: EntityId): Boolean {
    budget.charge()

    return world.isAlive(entityId)
  }

  override fun positionOf(entityId: EntityId): Vec3L? {
    budget.charge()

    return world.get(entityId, Position::class)?.toVec3L()
  }

  override fun masterIdOf(entityId: EntityId): Long? {
    budget.charge()

    return world.get(entityId, Master::class)?.masterId
  }

  override fun accountIdOf(entityId: EntityId): Long? {
    budget.charge()

    return world.get(entityId, Account::class)?.accountId
  }

  override fun entitiesInCube(centre: Vec3L, edge: Long, layers: Set<AoiLayer>): Set<EntityId> {
    budget.charge()

    val found = services.aoi.queryEntitiesInCube(centre, edge, layers)
    budget.chargeQueryResults(found.size)

    return found
  }

  override fun stationNear(around: Vec3L, kind: StaticEntityKind): EntityId? {
    budget.charge()

    return services.structures.stationNear(world, around, kind)
  }

  override fun spawnAreaEffect(centre: Vec3L, visualId: Long, effect: AreaEffect): EntityId {
    budget.charge(SPAWN_OPS)

    return services.areaEffectSpawner.spawn(world, centre, visualId, effect)
  }

  override fun placeStation(kind: StaticEntityKind, masterId: Long, at: Vec3L, yaw: Float): Boolean {
    budget.charge(SPAWN_OPS)

    return services.structures.place(world, kind, masterId, at, yaw) != null
  }

  override fun igniteGroundFire(centre: Vec3L, radiusTiles: Long): Boolean {
    budget.charge(SPAWN_OPS)

    return services.groundFire.ignite(centre, radiusTiles, casterId, skillId, skillLevel) != null
  }

  /**
   * A heal moves [Health] directly; damage is staged as a [IncomingDamage] so `ReceivedDamageSystem` drains
   * it, which is also what handles death, threat and interrupting the victim's own cast.
   *
   * Two casts landing on the same target share one component rather than one replacing the other: a cast
   * resolves between ticks, so no system is iterating and `World.add` applies immediately.
   */
  override fun apply(targetEntityId: EntityId, damage: Damage) {
    budget.charge()

    if (damage is Miss) {
      broadcastDamage(targetEntityId, damage)
      return
    }

    // `true` only from inside the scope, so it distinguishes "the entity is gone" from "the entity is here
    // but has no Health to heal" - a `when` returning Unit? would conflate the two.
    val landed = onEntity(targetEntityId) { target ->
      when (damage) {
        // CurMax.current clamps to [0, max] itself.
        is Heal -> get(target, Health::class)?.let { it.current += damage.amount }

        else -> {
          // This branch only - see Invulnerable: a miss and a heal stay true of a target that cannot be hurt.
          if (has(target, Invulnerable::class)) {
            return@onEntity false
          }

          val staged = get(target, IncomingDamage::class) ?: add(target, IncomingDamage())
          staged.add(damage.amount, casterId)
        }
      }

      true
    } == true

    // False means the blow never landed: the target died between the snapshot and now, which off-thread
    // resolution makes possible, or it cannot be hurt at all.
    if (landed) {
      broadcastDamage(targetEntityId, damage)
    }
  }

  override fun applyStatusEffect(targetEntityId: EntityId, effectId: Long, level: Int) {
    budget.charge()

    onEntity(targetEntityId) { target ->
      services.statusEffects.applyEffect(this, target, effectId, level, casterId)
    }
  }

  override fun applyStatusEffectIfAbsent(targetEntityId: EntityId, effectId: Long, level: Int): Boolean {
    budget.charge()

    return onEntity(targetEntityId) { target ->
      val present = get(target, StatusEffects::class)
        ?.activeEffects
        ?.any { it.definitionId == effectId } == true

      if (present) {
        return@onEntity false
      }

      services.statusEffects.applyEffect(this, target, effectId, level, casterId)

      true
    } ?: false
  }

  override fun consumeCasterMana(cost: Int): Boolean {
    if (cost <= 0) {
      return true
    }

    budget.charge()

    return onEntity(casterId) { caster ->
      val mana = get(caster, Mana::class) ?: return@onEntity true
      if (mana.current < cost) {
        return@onEntity false
      }

      mana.current -= cost
      true
    } ?: false
  }

  /** One op, though the service reads several components. */
  override fun offerRecipes(skillId: Long) {
    budget.charge()

    services.crafting.offerRecipes(world, casterId, skillId)
  }

  override fun readTracks(centre: Vec3L, radiusTiles: Long): TrackReading? {
    budget.charge()

    return services.spoor.read(centre, radiusTiles)
  }

  override fun survey(masterId: Long, accountId: Long?, centre: Vec3L, radiusMetres: Double) {
    budget.charge()

    services.survey.survey(
      masterId = masterId,
      accountId = accountId,
      entityId = casterId,
      centre = centre,
      radiusMetres = radiusMetres
    )
  }

  /** [block] against [id], or null if [id] is gone. */
  private inline fun <T> onEntity(id: EntityId, block: World.(EntityId) -> T): T? {
    return if (world.isAlive(id)) world.block(id) else null
  }

  private fun broadcastDamage(targetEntityId: EntityId, damage: Damage) {
    services.messages.sendToObserversOf(
      world,
      targetEntityId,
      DamageEntitySMSG(
        entityId = targetEntityId,
        sourceEntityId = casterId,
        attackId = skillId.toInt(),
        div = 1,
        damage = damage.amount,
        skillLevel = skillLevel,
        type = DamageEntitySMSG.DamageType.of(damage)
      )
    )
  }

  private companion object {
    /**
     * Creating an entity is a structural change plus several component adds, so it costs more than a read -
     * not because it holds the tick much longer, but so a script that spawns in a loop runs out of budget an
     * order of magnitude sooner than one that only looks around.
     */
    const val SPAWN_OPS = 8
  }
}
