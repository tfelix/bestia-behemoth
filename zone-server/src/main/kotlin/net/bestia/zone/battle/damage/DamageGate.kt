package net.bestia.zone.battle.damage

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.PlayerOwnership
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/**
 * The one answer to "may this harm land". Every path that hurts an entity asks here, so a new kind of
 * protection is one change in this object rather than one per path.
 */
object DamageGate {

  /** Folded into the reads of every system that asks, so the scheduler sees what the gate looks at. */
  val READS: ComponentClassSet = setOf(StatusEffects::class, GuildMembership::class) + PlayerOwnership.READS

  /**
   * Asked where damage is staged, not only where it is drained: by the drain the client has already been
   * shown a number it would never see subtracted.
   */
  fun verdict(world: World, sourceId: EntityId?, targetId: EntityId): Verdict {
    return verdict(world, sourceId, targetId, sourceOwner = sourceId?.let { PlayerOwnership.ownerAccountOf(world, it) })
  }

  /** For a source that can outlive whoever made it, like a fire: [sourceOwner] is the account it was made by. */
  fun verdict(world: World, sourceId: EntityId?, targetId: EntityId, sourceOwner: AccountId?): Verdict {
    if (isImmune(world, targetId)) {
      return Verdict.IMMUNE
    }

    if (isOwn(world, sourceId, targetId, sourceOwner) || isOwnGuilds(world, sourceId, targetId, sourceOwner)) {
      return Verdict.OWN
    }

    if (isWarded(world, sourceId, targetId, sourceOwner)) {
      return Verdict.WARDED
    }

    return Verdict.ADMITTED
  }

  /** For the paths that have no source to weigh: the drain, the weather, a trap. */
  fun isImmune(world: World, targetId: EntityId): Boolean {
    return hasShield(world, targetId, HarmShield.ALL)
  }

  /** Nothing harms itself, nor anything else its owner account owns: a master, its bestias, its stations. */
  private fun isOwn(world: World, sourceId: EntityId?, targetId: EntityId, sourceOwner: AccountId?): Boolean {
    if (sourceId == targetId) {
      return true
    }

    return sourceOwner != null && sourceOwner == PlayerOwnership.ownerAccountOf(world, targetId)
  }

  /** A guild's members do not harm what the guild owns. */
  private fun isOwnGuilds(world: World, sourceId: EntityId?, targetId: EntityId, sourceOwner: AccountId?): Boolean {
    val guildId = world.get(targetId, GuildOwned::class)?.guildId ?: return false

    return guildOf(world, sourceId, sourceOwner) == guildId
  }

  /** A master carries its guild; a bestia's is its owner's, found among the masters in the world. */
  private fun guildOf(world: World, sourceId: EntityId?, sourceOwner: AccountId?): Long? {
    sourceId?.let { world.get(it, GuildMembership::class) }?.let { return it.guildId }
    if (sourceOwner == null) {
      return null
    }

    var guildId: Long? = null
    world.query(GuildMembership::class, Account::class).each { _ ->
      if (get<Account>().accountId == sourceOwner) {
        guildId = get<GuildMembership>().guildId
      }
    }

    return guildId
  }

  /**
   * Harm between two player-owned sides while either one carries [HarmShield.PLAYERS]. A source that is gone can
   * carry no shield any more, so then only the target's counts.
   */
  private fun isWarded(world: World, sourceId: EntityId?, targetId: EntityId, sourceOwner: AccountId?): Boolean {
    if (sourceOwner == null || !PlayerOwnership.isPlayerOwned(world, targetId)) {
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
    OWN,
    WARDED,
  }
}
