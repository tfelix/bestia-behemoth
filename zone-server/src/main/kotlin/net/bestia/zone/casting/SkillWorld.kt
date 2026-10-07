package net.bestia.zone.casting

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.spoor.TrackReading

/**
 * Everything a [SkillStrategy] may do to the world, and nothing else.
 *
 * ### Why a facade rather than the world itself
 *
 * A script resolves on the tick thread between two ticks, so it may not do unbounded work: the next tick
 * cannot start while a cast resolves. Handing a script the [WorldView] would leave it free to do exactly
 * that. So this is a closed set of operations, each charged against the cast's budget. See
 * [BudgetedSkillWorld].
 *
 * What the budget bounds is therefore the *number* of operations, not the cost of one. Two of these are
 * not cheap: [placeStation] writes a row through JPA and [offerRecipes] sends a message, so each holds the
 * tick across a database round trip or a socket write. The honest ceiling on how long a cast holds the
 * tick is "ops x the slowest op", not "ops x short". Moving those two off the tick is the follow-up that
 * would make the budget mean what it says.
 *
 * ### Why the world is not injected
 *
 * A script that injected `World` or [WorldView] would reach the world without being charged against its
 * budget. The world reaches a script on its [SkillContext] and nowhere else.
 */
interface SkillWorld {

  /** World operations this cast may still spend. Zero means the next call fizzles it. */
  val remainingOps: Int

  // ------------------------------------------------------------------- reads

  fun isAlive(entityId: EntityId): Boolean

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

  /**
   * Sets light to the grass around [centre], if any of it will take.
   *
   * [radiusTiles] is a **radius**. False when nothing there is burnable, or when too many fires are already
   * running - a caller treats that exactly as it treats a skill landing on bare rock, because from the
   * player's side it is the same thing.
   */
  fun igniteGroundFire(centre: Vec3L, radiusTiles: Long): Boolean

  // ----------------------------------------------------------------- effects

  /** Lands [damage] on [targetEntityId] and shows the number to everyone who can see it. */
  fun apply(targetEntityId: EntityId, damage: Damage)

  fun applyStatusEffect(targetEntityId: EntityId, effectId: Long, level: Int)

  /**
   * Applies [effectId] only if [targetEntityId] does not already carry it, and answers whether it did.
   * Check and write happen in **one** operation, so another cast cannot land between them.
   *
   * This is how a script makes a status effect into a claim. The snapshot on [SkillContext.battle] carries
   * the same information, but only as of when the cast started, so a snapshot test followed by a write can
   * act on a stale answer. First Aid's once-a-minute limit is exactly this and nothing else.
   */
  fun applyStatusEffectIfAbsent(targetEntityId: EntityId, effectId: Long, level: Int): Boolean

  /** Drains the caster's mana, or false when it cannot pay - in which case nothing is spent. */
  fun consumeCasterMana(cost: Int): Boolean

  // --------------------------------------------------------------- crafting

  /** Sends the caster the list of what they can make where they are standing with [skillId]. */
  fun offerRecipes(skillId: Long)

  // ------------------------------------------------------------------ spoor

  /**
   * The heaviest traffic within [radiusTiles] of [centre], or null when the ground holds no tracks.
   *
   * A read rather than a message, so a script decides what a player is told and how much of it their skill
   * level has earned. Reaching the print store needs a world scope even though no component is involved -
   * see `SpoorService`.
   */
  fun readTracks(centre: Vec3L, radiusTiles: Long): TrackReading?

  // ------------------------------------------------------------- cartography

  /** Charts a disc of [radiusMetres] around [centre] for [masterId], reporting the outcome to [accountId]. */
  fun survey(masterId: Long, accountId: Long?, centre: Vec3L, radiusMetres: Double)

  /**
   * There is deliberately no `async` here. A cast resolves on the tick thread, so relational work belongs to
   * the service that owns the row, which hands it to `AsyncJobExecutor` under its own ordering key - as
   * `SurveyService` does for a chart.
   */
}
