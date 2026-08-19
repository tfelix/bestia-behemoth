package net.bestia.zone.battle.skill

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.ecs.AoiLayer
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.effects.AreaEffect
import net.bestia.zone.ecs.battle.effects.StatusEffects
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Mana
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.StaticEntityKind
import net.bestia.zone.ecs.battle.damage.Damage as DamageComponent
import kotlin.reflect.KClass

/**
 * The real [SkillWorld]: one short lock scope per operation, each charged against the cast's [SkillBudget].
 *
 * One instance per cast, because the budget is.
 */
class BudgetedSkillWorld(
  private val world: WorldView,
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

  override fun <T : Component> component(entityId: EntityId, type: KClass<T>): T? {
    budget.charge()

    return world.read { get(entityId, type) }
  }

  override fun positionOf(entityId: EntityId): Vec3L? {
    budget.charge()

    return world.read { get(entityId, Position::class)?.toVec3L() }
  }

  override fun masterIdOf(entityId: EntityId): Long? {
    budget.charge()

    return world.read { get(entityId, Master::class)?.masterId }
  }

  override fun accountIdOf(entityId: EntityId): Long? {
    budget.charge()

    return world.read { get(entityId, Account::class)?.accountId }
  }

  override fun entitiesInCube(centre: Vec3L, edge: Long, layers: Set<AoiLayer>): Set<EntityId> {
    budget.charge()

    val found = services.aoi.queryEntitiesInCube(centre, edge, layers)
    budget.chargeQueryResults(found.size)

    return found
  }

  override fun stationNear(around: Vec3L, kind: StaticEntityKind): EntityId? {
    budget.charge()

    return world.read { services.structures.stationNear(this, around, kind) }
  }

  override fun spawnAreaEffect(centre: Vec3L, visualId: Long, effect: AreaEffect): EntityId {
    budget.charge(SPAWN_OPS)

    return world.read { services.areaEffectSpawner.spawn(this, centre, visualId, effect) }
  }

  override fun placeStation(kind: StaticEntityKind, masterId: Long, at: Vec3L, yaw: Float): Boolean {
    budget.charge(SPAWN_OPS)

    return world.read { services.structures.place(this, kind, masterId, at, yaw) } != null
  }

  /**
   * A heal moves [Health] directly; damage is staged as a [DamageComponent] so `ReceivedDamageSystem` drains
   * it, which is also what handles death, threat and interrupting the victim's own cast.
   *
   * Two casts landing on the same target share one component rather than one replacing the other: the lock
   * is held for the whole scope and a tick cannot be iterating inside it, so `World.add` applies immediately
   * and the get-or-create is atomic against every other caster. That is what the old `world.defer { }` here
   * was working around when this ran on the tick thread.
   */
  override fun apply(targetEntityId: EntityId, damage: Damage) {
    budget.charge()

    if (damage is Miss) {
      broadcastDamage(targetEntityId, damage)
      return
    }

    // `true` only from inside the scope, so it distinguishes "the entity is gone" from "the entity is here
    // but has no Health to heal" - a `when` returning Unit? would conflate the two.
    val landed = world.modify(targetEntityId) { target ->
      when (damage) {
        // CurMax.current clamps to [0, max] itself.
        is Heal -> get(target, Health::class)?.let { it.current += damage.amount }

        else -> {
          val staged = get(target, DamageComponent::class) ?: add(target, DamageComponent())
          staged.add(damage.amount, casterId)
        }
      }

      true
    } == true

    // False means the target died between the snapshot and now, which off-thread resolution makes possible.
    if (landed) {
      broadcastDamage(targetEntityId, damage)
    }
  }

  override fun applyStatusEffect(targetEntityId: EntityId, effectId: Long, level: Int) {
    budget.charge()

    world.modify(targetEntityId) { target ->
      services.statusEffects.applyEffect(this, target, effectId, level, casterId)
    }
  }

  /** One `modify` scope for both halves, which is what makes the claim atomic against a concurrent cast. */
  override fun applyStatusEffectIfAbsent(targetEntityId: EntityId, effectId: Long, level: Int): Boolean {
    budget.charge()

    return world.modify(targetEntityId) { target ->
      val present = get(target, StatusEffects::class)
        ?.activeEffects
        ?.any { it.definitionId == effectId } == true

      if (present) {
        return@modify false
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

    return world.modify(casterId) { caster ->
      val mana = get(caster, Mana::class) ?: return@modify true
      if (mana.current < cost) {
        return@modify false
      }

      mana.current -= cost
      true
    } ?: false
  }

  /** One op, though the service reads several components: the whole answer is assembled in one lock scope. */
  override fun offerRecipes(skillId: Long) {
    budget.charge()

    world.read { services.crafting.offerRecipes(this, casterId, skillId) }
  }

  override fun survey(masterId: Long, accountId: Long?, centre: Vec3L, radiusMetres: Double) {
    budget.charge()

    services.survey.survey(
      world = world,
      masterId = masterId,
      accountId = accountId,
      entityId = casterId,
      centre = centre,
      radiusMetres = radiusMetres
    )
  }

  private fun broadcastDamage(targetEntityId: EntityId, damage: Damage) {
    val at = world.read { get(targetEntityId, Position::class)?.toVec3L() } ?: return

    services.messages.sendToAllPlayersInRange(
      at,
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
     * not because the lock is held much longer, but so a script that spawns in a loop runs out of budget an
     * order of magnitude sooner than one that only looks around.
     */
    const val SPAWN_OPS = 8
  }
}
