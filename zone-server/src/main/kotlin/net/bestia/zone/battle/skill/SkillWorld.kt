package net.bestia.zone.battle.skill

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.AoiLayer
import net.bestia.zone.ecs.battle.effects.AreaEffect
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.StaticEntityKind
import kotlin.reflect.KClass

/**
 * Everything a [SkillStrategy] may do to the world, and nothing else.
 *
 * ### Why a facade rather than the world itself
 *
 * A script runs off the tick thread, so every read and write has to happen inside a lock-holding scope -
 * the trap [WorldView] exists to close. Handing a script the [WorldView] would leave it free to open one
 * scope and do unbounded work inside it, which is the one thing that must not happen: a tick cannot start
 * while a scope is open. So this is a closed set of operations, each of which opens its own scope and is
 * charged against the cast's budget. See [BudgetedSkillWorld].
 *
 * What the budget bounds is therefore the *number* of scopes, not the cost of one. Two of these are not
 * cheap: [placeStation] writes a row through JPA and [offerRecipes] sends a message, both inside their
 * scope, so each holds the world lock across a database round trip or a socket write. That is inherited -
 * the same work happened under the same lock when skills resolved on the tick thread - but it means the
 * honest ceiling on a cast's lock occupancy is "ops x the slowest op", not "ops x short". Moving those two
 * off the lock is the follow-up that would make the budget mean what it says.
 *
 * ### Why the world is not injected
 *
 * A script is a Spring bean collected into [SkillStrategyFactory], which [SkillExecutionService] depends
 * on, which `CastingSystem` depends on, which the `World` bean is assembled from. A script that injected
 * `World` or [WorldView] would close that cycle and the context would fail at boot - the same cycle
 * `SurveyService` documents. The world reaches a script on its [SkillContext] and nowhere else.
 */
interface SkillWorld {

  /** World operations this cast may still spend. Zero means the next call fizzles it. */
  val remainingOps: Int

  // ------------------------------------------------------------------- reads

  fun isAlive(entityId: EntityId): Boolean

  /**
   * The live component, for the cases the named accessors below do not cover. Read what you need off it and
   * do not hold on to it: the lock scope closes when this returns, so mutating the result afterwards races
   * the tick, which is the whole reason [WorldView] hides `get`.
   */
  fun <T : Component> component(entityId: EntityId, type: KClass<T>): T?

  fun positionOf(entityId: EntityId): Vec3L?

  /** Null when [entityId] is a bestia rather than a master - a refusal for anything master-only. */
  fun masterIdOf(entityId: EntityId): Long?

  fun accountIdOf(entityId: EntityId): Long?

  // ----------------------------------------------------------------- queries

  /**
   * Entities within a cube of [edge] tiles centred on [centre]. Charged per result as well as per call -
   * the cost of a spatial query is the answer, not the asking.
   */
  fun entitiesInCube(centre: Vec3L, edge: Long, layers: Set<AoiLayer> = AoiLayer.ALL): Set<EntityId>

  /** The nearest station of [kind] in reach of [around], or null. */
  fun stationNear(around: Vec3L, kind: StaticEntityKind): EntityId?

  // ------------------------------------------------------------------ spawns

  /**
   * Drops a patch of ground effect at [centre], returning the entity carrying it.
   *
   * Implemented over `WorldView.read`, which reads oddly for something that creates an entity: the spawner
   * takes a `World`, and `read` is the only scope that hands one over without already naming an entity. The
   * lock is the same either way, so this is a naming mismatch and not a correctness one.
   */
  fun spawnAreaEffect(centre: Vec3L, visualId: Long, effect: AreaEffect): EntityId

  /** Puts a station up at [at] for [masterId]. False when the ground already holds one of the same kind. */
  fun placeStation(kind: StaticEntityKind, masterId: Long, at: Vec3L, yaw: Float = 0f): Boolean

  // ----------------------------------------------------------------- effects

  /** Lands [damage] on [targetEntityId] and shows the number to everyone who can see it. */
  fun apply(targetEntityId: EntityId, damage: Damage)

  fun applyStatusEffect(targetEntityId: EntityId, effectId: Long, level: Int)

  /**
   * Applies [effectId] only if [targetEntityId] does not already carry it, and answers whether it did.
   * Check and write happen in **one** lock scope, so it is safe against another cast landing between them.
   *
   * This is how a script makes a status effect into a claim. The snapshot on [SkillContext.battle] carries
   * the same information, but only as of when the cast started - and off-thread resolution means two casts of
   * one skill can be in flight at once, so a snapshot test followed by a write is a race. First Aid's
   * once-a-minute limit is exactly this and nothing else.
   */
  fun applyStatusEffectIfAbsent(targetEntityId: EntityId, effectId: Long, level: Int): Boolean

  /** Drains the caster's mana, or false when it cannot pay - in which case nothing is spent. */
  fun consumeCasterMana(cost: Int): Boolean

  // --------------------------------------------------------------- crafting

  /** Sends the caster the list of what they can make where they are standing with [skillId]. */
  fun offerRecipes(skillId: Long)

  // ------------------------------------------------------------- cartography

  /** Charts a disc of [radiusMetres] around [centre] for [masterId], reporting the outcome to [accountId]. */
  fun survey(masterId: Long, accountId: Long?, centre: Vec3L, radiusMetres: Double)

  /**
   * There is deliberately no `async` here, and no message send. A cast already runs on a background worker,
   * so a script may do its own relational work inline - that is the point of resolving off the tick thread,
   * and it is why `SurveyService`'s hand-off through `AsyncJobExecutor` is no longer the only way to write a
   * chart. A script that needs to *order* work against another writer of the same row should say so through
   * the service that owns that row, which is where the ordering key belongs.
   */
}
