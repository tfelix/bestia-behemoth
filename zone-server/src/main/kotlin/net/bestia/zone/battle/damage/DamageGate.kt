package net.bestia.zone.battle.damage

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.ClosedToAttack
import net.bestia.zone.entity.ecs.PlayerOwnership
import net.bestia.zone.entity.ecs.WardProtected
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.skill.SkillId
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/**
 * The one answer to "may this harm land". Every path that hurts an entity asks here, so a new kind of
 * protection is one change in this object rather than one per path.
 */
object DamageGate {

  /** Folded into the reads of every system that asks, so the scheduler sees what the gate looks at. */
  val READS: ComponentClassSet =
    setOf(StatusEffects::class, GuildMembership::class, WardProtected::class, ClosedToAttack::class) + PlayerOwnership.READS

  /**
   * Asked where damage is staged, not only where it is drained: by the drain the client has already been
   * shown a number it would never see subtracted.
   */
  fun verdict(world: World, sourceId: EntityId?, targetId: EntityId): Verdict {
    return verdict(world, sourceId, targetId, sourceOwner = sourceId?.let { PlayerOwnership.ownerAccountOf(world, it) })
  }

  /** For a source that can outlive whoever made it, like a fire: [sourceOwner] is the account it was made by. */
  fun verdict(world: World, sourceId: EntityId?, targetId: EntityId, sourceOwner: AccountId?): Verdict {
    if (hasShield(world, targetId, HarmShield.GRACE)) {
      return Verdict.GRACE
    }

    if (isImmune(world, targetId)) {
      return Verdict.IMMUNE
    }

    if (isOwn(world, sourceId, targetId, sourceOwner)) {
      return Verdict.OWN
    }

    val owningGuild = world.get(targetId, GuildOwned::class)?.guildId
    val attackersGuild = owningGuild?.let { membershipOf(world, sourceId, sourceOwner) }
    if (owningGuild != null && attackersGuild?.guildId == owningGuild) {
      return Verdict.OWN
    }

    if (isWarded(world, sourceId, targetId, sourceOwner)) {
      return Verdict.WARDED
    }

    if (owningGuild != null && !isApproved(attackersGuild)) {
      return Verdict.UNAPPROVED
    }

    if (world.has(targetId, ClosedToAttack::class)) {
      return Verdict.CLOSED
    }

    return Verdict.ADMITTED
  }

  /** For the paths that have no source to weigh: the drain, the weather, a trap. */
  fun isImmune(world: World, targetId: EntityId): Boolean {
    return hasShield(world, targetId, HarmShield.ALL) || hasShield(world, targetId, HarmShield.GRACE)
  }

  /** Nothing harms itself, nor anything else its owner account owns: a master, its bestias, its stations. */
  private fun isOwn(world: World, sourceId: EntityId?, targetId: EntityId, sourceOwner: AccountId?): Boolean {
    if (sourceId == targetId) {
      return true
    }

    return sourceOwner != null && sourceOwner == PlayerOwnership.ownerAccountOf(world, targetId)
  }

  /** Guild structures are a matter between guilds the realm recognises. Mobs and guildless masters stay out. */
  private fun isApproved(membership: GuildMembership?): Boolean {
    return membership != null && membership.skillLevel(SkillId.OFFICIAL_GUILD_APPROVAL.name) > 0
  }

  /** A master carries its guild; a bestia's is its owner's, found among the masters in the world. */
  private fun membershipOf(world: World, sourceId: EntityId?, sourceOwner: AccountId?): GuildMembership? {
    sourceId?.let { world.get(it, GuildMembership::class) }?.let { return it }
    if (sourceOwner == null) {
      return null
    }

    var membership: GuildMembership? = null
    world.query(GuildMembership::class, Account::class).each { _ ->
      if (get<Account>().accountId == sourceOwner) {
        membership = get<GuildMembership>()
      }
    }

    return membership
  }

  /**
   * Harm from a player to another player's side, or to a townsperson, while either one carries
   * [HarmShield.PLAYERS]. A source that is gone can carry no shield any more, so then only the target's counts.
   */
  private fun isWarded(world: World, sourceId: EntityId?, targetId: EntityId, sourceOwner: AccountId?): Boolean {
    val targetAnswersToWards = PlayerOwnership.isPlayerOwned(world, targetId) || world.has(targetId, WardProtected::class)
    if (sourceOwner == null || !targetAnswersToWards) {
      return false
    }

    val sourceIsWarded = sourceId != null && hasShield(world, sourceId, HarmShield.PLAYERS)

    return sourceIsWarded || hasShield(world, targetId, HarmShield.PLAYERS)
  }

  private fun hasShield(world: World, id: EntityId, shield: HarmShield): Boolean {
    return world.get(id, StatusEffects::class)?.hasShield(shield) == true
  }

  enum class Verdict {
    ADMITTED,
    IMMUNE,

    /** Unharmable for a while, such as an Empyrite right after its capture. */
    GRACE,
    OWN,
    WARDED,

    /** A guild structure, and the source is not of a guild with Official Guild Approval. */
    UNAPPROVED,

    /** A guild structure on a Stronghold's grounds, while no attack window is open. */
    CLOSED,
  }
}
