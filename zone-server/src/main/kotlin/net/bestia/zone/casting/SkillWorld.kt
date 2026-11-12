package net.bestia.zone.casting

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ammo.Ammunition
import net.bestia.zone.battle.attack.BattleAttack
import net.bestia.zone.skill.PartyMates
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.SlotLock
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

  /** Whether [entityId] lies dead where it fell, waiting for a respawn or a Resurrection. */
  fun isFallen(entityId: EntityId): Boolean

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

  /**
   * Lands [damage] on [targetEntityId] and shows the number to everyone who can see it. A blow is weighed by both
   * sides' damage modifiers first, as [kind] - the skill's own kind when null, see [HitKind.ofSkill].
   */
  fun apply(targetEntityId: EntityId, damage: Damage, kind: HitKind? = null)

  /**
   * [amount] goes to the effect instance, like the element an endowment gives a weapon or the strength a skill's
   * level table gives a debuff. [durationSeconds] replaces the effect's own, for a skill whose table says how long.
   */
  fun applyStatusEffect(
    targetEntityId: EntityId,
    effectId: Long,
    level: Int,
    amount: Int = 0,
    durationSeconds: Double? = null,
  )

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

  /**
   * The caster's party members within [radiusMetres] of the caster and the bestias they own, the caster included;
   * the caster and its own bestias alone without a party. One operation plus one per result, see [PartyMates].
   */
  fun partyNear(radiusMetres: Long = PartyMates.VISIBLE_RANGE_METRES): List<EntityId>

  /** Whether [entityId] is the caster, one of its party, or a bestia one of them owns. One operation. */
  fun isPartyOf(entityId: EntityId): Boolean

  /**
   * A weapon blow at [targetEntityId] with [attack] at [powerPercent] of a swing: it hits, misses and crits like
   * one, lands like [apply] does, and comes back for the script's own after-effects, as `Blocked` when a shield
   * caught it. [offHand] strikes with the second weapon in the left hand. Null when there is no fight to resolve, or
   * no second weapon. One operation.
   */
  fun strike(targetEntityId: EntityId, attack: BattleAttack, powerPercent: Int, offHand: Boolean = false): Damage?

  /** One shot for the caster's held bow or crossbow, taken from the quiver; null when none is left. One operation. */
  fun drawAmmo(): Ammunition?

  /** Whether the caster stands in the half behind [targetEntityId], as it faces. One operation. */
  fun isBehind(targetEntityId: EntityId): Boolean

  /** The tile right behind [targetEntityId], as it faces; null when it has no facing. One operation. */
  fun spotBehind(targetEntityId: EntityId): Vec3L?

  /**
   * Brings the caster's bestias that are out in the world to stand around the caster, leaving out any that is
   * fighting. Returns how many came. One operation plus one per bestia.
   */
  fun callOwnBestias(): Int

  /**
   * Plants a sapling in the tree stump at [at], growing at [growthPercent] of the usual time and carrying
   * [bonusYield] extra loot for when it is felled grown. False when no stump stands there. One operation.
   */
  fun replant(at: Vec3L, growthPercent: Int, bonusYield: Int): Boolean

  /** Makes the burning campfire near [at] the caster's Base Camp for [durationSeconds]; false without one. One op. */
  fun pitchCamp(at: Vec3L, level: Int, durationSeconds: Long): Boolean

  /**
   * Shows the caster the nearest ore, rare herbs, timber and game within [rangeMetres], each blurred less the higher
   * [accuracy] (0 to 1). A scan of the land: charged as a spawn.
   */
  fun senseResources(rangeMetres: Double, accuracy: Double)

  /** Shows the caster the mana at work around it, as much as Magic Sense at [level] tells. One scan. */
  fun senseMagic(level: Int)

  /** Shows the caster what Sense reads off [targetId]; false when there is nothing to read. One op. */
  fun senseMonster(targetId: EntityId): Boolean

  /** Puts the caster's street stall down where they stand, with [maxListings] places, for [seconds]. One spawn. */
  fun pitchStall(maxListings: Int, seconds: Int): OpError?

  /** Teleports the caster and its own bestias close by: nearby, or home if [toHome]. One spawn. */
  fun teleportCaster(toHome: Boolean)

  /** The caster's [index]-th saved Warp Portal spot, oldest first; null when there is none. One op. */
  fun warpMarkOf(entityId: EntityId, index: Int): Vec3L?

  /** Opens a portal at [at] to [to], or home when null, for [passages] bodies and [seconds]. One spawn. */
  fun openPortal(at: Vec3L, to: Vec3L?, passages: Int, seconds: Int)

  /** Why [bestiaId] cannot learn from the caster's spell scroll [scrollId], or null when it can. One op. */
  fun spellTrainingRefusal(bestiaId: EntityId, scrollId: Long): OpError?

  /** Why the caster cannot copy [skillId] off [bestiaId] onto a scroll, or null when it can. One op. */
  fun spellInscriptionRefusal(bestiaId: EntityId, skillId: Long): OpError?

  /** Takes the spell scroll [scrollId] out of the caster's bag; what it held, or null when it is gone. One op. */
  fun takeSpellScroll(scrollId: Long): InscribedSpell?

  /** [skillId] as [bestiaId] knows it, or null when it knows no such attack. One op. */
  fun bestiaAttack(bestiaId: EntityId, skillId: Long): InscribedSpell?

  /** [bestiaId] knows [spell] from now on. One op. */
  fun teachSpell(bestiaId: EntityId, spell: InscribedSpell)

  /** Writes [spell] onto a new scroll in the caster's bag. One op. */
  fun inscribeSpell(spell: InscribedSpell)

  /** Loses the caster's own [bestiaId] for good, see [SpellScrolls.breakMind]. One spawn. */
  fun breakMind(bestiaId: EntityId)

  /** Tells the caster's player why the cast came to nothing. One op. */
  fun tellCaster(error: OpError)

  /** Shows the caster the hollows within [radiusMetres], and how deep and large they are if [revealsDepth]. */
  fun soundForHollows(radiusMetres: Int, revealsDepth: Boolean)

  /**
   * Pushes [entityId] up to [tiles] away from [from], less when it resists a knockback, see
   * [net.bestia.zone.battle.Knockback]. Returns the tiles it moved. One operation.
   */
  fun knockBack(entityId: EntityId, from: Vec3L, tiles: Int): Int

  /** Gives the caster [amount] of [itemId], through the usual weight check on the next tick. One operation. */
  fun giveItem(itemId: Long, amount: Int)

  /** Raises a dead [entityId] where it lies with [healthPercent] of its health. False for the living. One operation. */
  fun revive(entityId: EntityId, healthPercent: Int): Boolean

  /** Puts [entityId] on the ground at [to] at once, a shadow step; false with no ground there. One operation. */
  fun blink(entityId: EntityId, to: Vec3L): Boolean

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

  // ------------------------------------------------------------- settlements

  /** Plants the sign site of the town the caster planned, where it stands. False when it may not. */
  fun foundTown(): Boolean

  /** Starts a construction site of [kind] owned by the caster at [at]. @return its structure id, or null */
  fun beginStructure(kind: StaticEntityKind, at: Vec3L, buildSeconds: Float): Long?

  /**
   * There is deliberately no `async` here. A cast resolves on the tick thread, so relational work belongs to
   * the service that owns the row, which hands it to `AsyncJobExecutor` under its own ordering key.
   */

  /**
   * Walks [entityId] in a straight line towards [towards] at once, up to [maxTiles] tiles and stopping
   * [keepDistance] tiles short, or at the first step it could not take. Returns the tiles it moved.
   */
  fun forceMove(entityId: EntityId, towards: Vec3L, maxTiles: Int, keepDistance: Int = 0): Int

  /** Rolls [status], a `StatusEffectId` name, on [targetEntityId] against its resistance; whether it landed. */
  fun inflict(
    targetEntityId: EntityId,
    status: String,
    chancePercent: Int,
    durationSeconds: Double?,
    level: Int,
    amount: Int = 0,
  ): Boolean

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

  /** Gives the caster back [staminaPercent] of its stamina pool and heals [healthPercent] of its HP. One operation. */
  fun restoreCaster(staminaPercent: Int, healthPercent: Int)

  /** Takes [percent] of [entityId]'s current mana away, to nobody. */
  fun drainMana(entityId: EntityId, percent: Int)

  fun hasEffect(entityId: EntityId, effectId: Long): Boolean

  /** Who gave [entityId] its [effectId], or null when it has none or nobody gave it. One operation. */
  fun effectSourceOf(entityId: EntityId, effectId: Long): EntityId?

  fun removeEffect(entityId: EntityId, effectId: Long)

  /** Strips every buff a dispel may lift from [entityId]; returns how many went. */
  fun dispel(entityId: EntityId): Int

  /**
   * The Sage's Dispel: rolls [baseChancePercent] against each dispellable buff and curse on [entityId], cut by a
   * stronger spell than the cast's Potency. Returns how many went.
   */
  fun dispelAll(entityId: EntityId, baseChancePercent: Int): Int

  /** Tries to break each enchantment on a ground item at `baseChancePercent - strength`; returns how many broke. */
  fun dispelGroundItem(entityId: EntityId, baseChancePercent: Int): Int

  /** How full [entityId]'s mana pool is, from 0 to 1; 0 for something with no pool. */
  fun manaFractionOf(entityId: EntityId): Float

  /** Empties the caster's mana pool and returns what it held. */
  fun spendAllCasterMana(): Int

  /** Whether the caster landed a Triple Blow so lately that a Chain Combo may still follow. One operation. */
  fun followsTripleBlow(): Boolean

  /** Spends the caster's last Triple Blow on one Chain Combo; false when it is too late. One operation. */
  fun spendTripleBlow(): Boolean

  /** Takes off what [entityId] wears in [slot]; false when nothing was there. */
  fun strip(entityId: EntityId, slot: EquipmentSlot): Boolean

  /** The pieces [entityId] wears that a strip may take, by the slot lock each one gets. One operation. */
  fun strippableGearOf(entityId: EntityId): List<SlotLock>

  /** Whether [entityId] holds a weapon in each hand. One operation. */
  fun isDualWielding(entityId: EntityId): Boolean

  /** What [entityId] holds in its right hand, or null for bare hands. One operation. */
  fun heldWeaponTypeOf(entityId: EntityId): WeaponType?

  /** Whether [entityId] holds a shield in its left hand. One operation. */
  fun holdsShield(entityId: EntityId): Boolean

  /** The percent chance [entityId]'s gear gives each landed swing to put [effect] on its target. One operation. */
  fun swingStatusChanceOf(entityId: EntityId, effect: StatusEffectId): Int

  /** Takes [percent] of the maximum off the durability of [entityId]'s weapon and armour. */
  fun wearGear(entityId: EntityId, percent: Int): Boolean

  /** Lets the caster sense masters and player bestia around it for [seconds], see `Detecting`. */
  fun detect(seconds: Float)
}
