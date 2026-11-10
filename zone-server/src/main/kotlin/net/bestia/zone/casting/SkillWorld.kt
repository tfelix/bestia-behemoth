package net.bestia.zone.casting

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.message.SMSG
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
 * What the budget bounds is therefore the *number* of operations, not the cost of one. [offerRecipes] is
 * not cheap: it sends a message, so it holds the tick across a socket write. The honest ceiling on how long
 * a cast holds the tick is "ops x the slowest op", not "ops x short". Moving it off the tick is the
 * follow-up that would make the budget mean what it says.
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

  fun guildMembershipOf(entityId: EntityId): GuildMembership?

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

  /** The caster says [text] out loud, see [Speech]. */
  fun say(text: String)

  /** Drops [amount] of [itemId] on the ground at [at], for the caster's account to pick up first. */
  fun dropItem(itemId: Long, amount: Int, at: Vec3L)

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

  /** [amount] goes to the effect instance, like the element an endowment gives a weapon. */
  fun applyStatusEffect(targetEntityId: EntityId, effectId: Long, level: Int, amount: Int = 0)

  /**
   * Applies [effectId] only if [targetEntityId] does not already carry it, and answers whether it did.
   * Check and write happen in **one** operation, so another cast cannot land between them.
   *
   * This is how a script makes a status effect into a claim. The snapshot on [SkillContext.battle] carries
   * the same information, but only as of when the cast started, so a snapshot test followed by a write can
   * act on a stale answer. First Aid's once-a-minute limit is exactly this and nothing else.
   */
  fun applyStatusEffectIfAbsent(targetEntityId: EntityId, effectId: Long, level: Int): Boolean

  /**
   * Puts [effectId] on everything [guildId]'s members have within [radiusMetres] of [centre]. One operation
   * however big the guild: a guild skill reaches every member, which no per-member budget could afford.
   */
  fun applyStatusEffectToGuildNear(guildId: Long, centre: Vec3L, radiusMetres: Long, effectId: Long, level: Int)

  /** Heals [percent] of the HP and mana of everything living of [guildId]'s members near [centre]. One operation. */
  fun restoreGuildNear(guildId: Long, centre: Vec3L, radiusMetres: Long, percent: Int)

  /**
   * Moves what every member of [guildId] plays to [to], spread over the tiles around it, the caster included
   * only when [includeCaster]. The dead stay where they fell. One operation.
   */
  fun summonGuild(guildId: Long, to: Vec3L, includeCaster: Boolean)

  /** Sends [message] to every member of [guildId] being played. One operation. */
  fun tellGuild(guildId: Long, message: SMSG)

  /**
   * Drains the caster's [mana] and [stamina] together, or returns false when it cannot pay both - in which case
   * nothing is spent. One operation.
   */
  fun payCasterCost(mana: Int, stamina: Int): Boolean

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

  /**
   * Walks [entityId] in a straight line towards [towards] at once, up to [maxTiles] tiles and stopping
   * [keepDistance] tiles short, or at the first step it could not take. Returns the tiles it moved.
   */
  fun forceMove(entityId: EntityId, towards: Vec3L, maxTiles: Int, keepDistance: Int = 0): Int

  /** Rolls [status], a `StatusEffectId` name, on [targetEntityId] against its resistance; whether it landed. */
  fun inflict(targetEntityId: EntityId, status: String, chancePercent: Int, durationSeconds: Double?, level: Int): Boolean

  /** Breaks off whatever [targetEntityId] was casting or crafting. */
  fun interrupt(targetEntityId: EntityId)

  /** Whether the caster may hurt [entityId]: alive, hurtable, on the other side and past the damage gate. */
  fun isFoe(entityId: EntityId): Boolean

  /** What [entityId] is made of, or null for something with no nature, like a master. */
  fun natureOf(entityId: EntityId): Nature?

  /** What [entityId] carries, in kilograms. */
  fun carriedKilogramsOf(entityId: EntityId): Int

  /** Adds mana to [entityId], up to its pool. */
  fun restoreMana(entityId: EntityId, amount: Int)

  fun hasEffect(entityId: EntityId, effectId: Long): Boolean

  fun removeEffect(entityId: EntityId, effectId: Long)

  /** Strips every buff a dispel may lift from [entityId]; returns how many went. */
  fun dispel(entityId: EntityId): Int

  /** Tries to break each enchantment on a ground item at `baseChancePercent - strength`; returns how many broke. */
  fun dispelGroundItem(entityId: EntityId, baseChancePercent: Int): Int

  /** How full [entityId]'s mana pool is, from 0 to 1; 0 for something with no pool. */
  fun manaFractionOf(entityId: EntityId): Float

  /** Empties the caster's mana pool and returns what it held. */
  fun spendAllCasterMana(): Int

  /** Takes off what [entityId] wears in [slot]; false when nothing was there. */
  fun strip(entityId: EntityId, slot: EquipmentSlot): Boolean

  /** Takes [percent] of the maximum off the durability of [entityId]'s weapon and armour. */
  fun wearGear(entityId: EntityId, percent: Int): Boolean

  /** Lets the caster sense masters and player bestia around it for [seconds], see `Detecting`. */
  fun detect(seconds: Float)
}
