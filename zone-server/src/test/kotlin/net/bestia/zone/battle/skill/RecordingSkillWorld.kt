package net.bestia.zone.battle.skill

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.ecs.AoiLayer
import net.bestia.zone.ecs.battle.effects.AreaEffect
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.StaticEntityKind
import kotlin.reflect.KClass

/**
 * A [SkillWorld] that records what a script did to it instead of doing it.
 *
 * Script tests assert on *effects* rather than on a return value, because that is where a script's behaviour
 * now lives - `Ember` returns nothing at all, and what makes it Ember is the patch it spawns. Answers to
 * reads are set up as fields so a test can put the caster somewhere and make it a master.
 */
class RecordingSkillWorld(
  private val positions: MutableMap<EntityId, Vec3L> = mutableMapOf(),
  private val masterIds: MutableMap<EntityId, Long> = mutableMapOf(),
  private val accountIds: MutableMap<EntityId, Long> = mutableMapOf(),
  private val stations: MutableMap<StaticEntityKind, EntityId> = mutableMapOf(),
  private val entitiesNearby: Set<EntityId> = emptySet(),

  /** What [placeStation] answers - false models ground that already holds one. */
  var stationPlacementSucceeds: Boolean = true,

  /** What [consumeCasterMana] answers - false models a caster who cannot pay. */
  var manaAvailable: Boolean = true,
) : SkillWorld {

  val spawnedAreaEffects = mutableListOf<SpawnedAreaEffect>()
  val placedStations = mutableListOf<PlacedStation>()
  val appliedDamage = mutableListOf<Pair<EntityId, Damage>>()
  val appliedEffects = mutableListOf<AppliedEffect>()
  val recipeOffers = mutableListOf<Long>()
  val surveys = mutableListOf<Survey>()
  val manaSpent = mutableListOf<Int>()

  override val remainingOps: Int = Int.MAX_VALUE

  fun putPosition(entityId: EntityId, at: Vec3L) = apply { positions[entityId] = at }

  fun putMaster(entityId: EntityId, masterId: Long, accountId: Long? = null) = apply {
    masterIds[entityId] = masterId
    accountId?.let { accountIds[entityId] = it }
  }

  fun putStation(kind: StaticEntityKind, entityId: EntityId) = apply { stations[kind] = entityId }

  override fun isAlive(entityId: EntityId): Boolean = true

  override fun <T : Component> component(entityId: EntityId, type: KClass<T>): T? = null

  override fun positionOf(entityId: EntityId): Vec3L? = positions[entityId]

  override fun masterIdOf(entityId: EntityId): Long? = masterIds[entityId]

  override fun accountIdOf(entityId: EntityId): Long? = accountIds[entityId]

  override fun entitiesInCube(centre: Vec3L, edge: Long, layers: Set<AoiLayer>): Set<EntityId> = entitiesNearby

  override fun stationNear(around: Vec3L, kind: StaticEntityKind): EntityId? = stations[kind]

  override fun spawnAreaEffect(centre: Vec3L, visualId: Long, effect: AreaEffect): EntityId {
    spawnedAreaEffects += SpawnedAreaEffect(centre, visualId, effect)

    return spawnedAreaEffects.size.toLong()
  }

  /** What a script asked to set light to, so `Ember`'s two separate effects can be told apart in a test. */
  val ignitions = mutableListOf<Ignition>()

  data class Ignition(val centre: Vec3L, val radiusTiles: Long)

  /** True, because a fake ground has no reason to refuse. A test wanting refusal overrides this. */
  override fun igniteGroundFire(centre: Vec3L, radiusTiles: Long): Boolean {
    ignitions += Ignition(centre, radiusTiles)

    return true
  }

  override fun placeStation(kind: StaticEntityKind, masterId: Long, at: Vec3L, yaw: Float): Boolean {
    placedStations += PlacedStation(kind, masterId, at)

    return stationPlacementSucceeds
  }

  override fun apply(targetEntityId: EntityId, damage: Damage) {
    appliedDamage += targetEntityId to damage
  }

  override fun applyStatusEffect(targetEntityId: EntityId, effectId: Long, level: Int) {
    appliedEffects += AppliedEffect(targetEntityId, effectId, level)
  }

  /** Models the real atomic claim: the first caller wins the effect and every later one is refused. */
  override fun applyStatusEffectIfAbsent(targetEntityId: EntityId, effectId: Long, level: Int): Boolean {
    if (appliedEffects.any { it.targetEntityId == targetEntityId && it.effectId == effectId }) {
      return false
    }

    applyStatusEffect(targetEntityId, effectId, level)

    return true
  }

  override fun consumeCasterMana(cost: Int): Boolean {
    if (!manaAvailable) {
      return false
    }

    manaSpent += cost

    return true
  }

  override fun offerRecipes(skillId: Long) {
    recipeOffers += skillId
  }

  override fun survey(masterId: Long, accountId: Long?, centre: Vec3L, radiusMetres: Double) {
    surveys += Survey(masterId, accountId, centre, radiusMetres)
  }

  data class SpawnedAreaEffect(val centre: Vec3L, val visualId: Long, val effect: AreaEffect)

  data class PlacedStation(val kind: StaticEntityKind, val masterId: Long, val at: Vec3L)

  data class AppliedEffect(val targetEntityId: EntityId, val effectId: Long, val level: Int)

  data class Survey(val masterId: Long, val accountId: Long?, val centre: Vec3L, val radiusMetres: Double)
}
