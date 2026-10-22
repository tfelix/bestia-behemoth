package net.bestia.zone.aoi

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.ecs.core.World
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Who is told about an entity: the accounts that see it, plus always the account that plays it.
 *
 * One rule for component state and one-off events alike, so a client never sees a health drop without the hit.
 */
@Component
class EntityAudience(
  private val entityVisibility: EntityVisibility,
  private val sessions: ConnectionInfoService,
) {

  /**
   * The player hears about their own master and bestias wherever these stand, so the map can show them, and so
   * the entity they drive is not lost while the chunk it walks into is not sent yet. Tick thread, or under the
   * world lock.
   */
  fun of(world: World, entityId: EntityId): Set<AccountId> {
    val observers = entityVisibility.observersOf(entityId)
    val owner = ownerOf(world, entityId) ?: return observers

    return if (owner in observers) observers else observers + owner
  }

  /**
   * The account playing [entityId] right now, as its master or one of that master's bestias. Null for anything
   * else, and for a bestia whose owner logged out or plays another master.
   */
  fun ownerOf(world: World, entityId: EntityId): AccountId? {
    val accountId = world.get(entityId, Account::class)?.accountId ?: return null
    val masterId = world.get(entityId, Master::class)?.masterId
      ?: world.get(entityId, OwnedBestia::class)?.masterId
      ?: return null

    return if (sessions.activeMasterIdOf(accountId) == masterId) accountId else null
  }

  /**
   * Who hears a [net.bestia.zone.sync.SyncTargets.OwnerAndParty] change: the owner, plus every party member
   * playing the master the party lists, wherever they stand. A master is heard by its party while it is being
   * played; a bestia only while it is the entity its owner plays, so the party's window can show what each
   * member is fighting with. Tick thread, or under the world lock.
   */
  fun ownerAndParty(world: World, entityId: EntityId): Set<AccountId> {
    val owner = world.get(entityId, Account::class)?.accountId ?: return emptySet()
    val masterEntityId = partyAnchorOf(world, entityId, owner) ?: return setOf(owner)
    val membership = world.get(masterEntityId, PartyMembership::class) ?: return setOf(owner)

    return ownerAndPlaying(owner, membership.members) { it.accountId to it.masterId }
  }

  /**
   * Who hears a [net.bestia.zone.sync.SyncTargets.OwnerAndGuild] change: the owner, plus every guild member
   * playing the master the guild lists, wherever they stand. Only a master is in a guild. Tick thread, or under
   * the world lock.
   */
  fun ownerAndGuild(world: World, entityId: EntityId): Set<AccountId> {
    val owner = world.get(entityId, Account::class)?.accountId ?: return emptySet()
    val membership = world.get(entityId, GuildMembership::class) ?: return setOf(owner)

    return ownerAndPlaying(owner, membership.members) { it.accountId to it.masterId }
  }

  /** [owner], plus the account of each of [members] whose account plays that member's master. */
  private fun <T> ownerAndPlaying(
    owner: AccountId,
    members: Collection<T>,
    accountAndMaster: (T) -> Pair<AccountId, Long>,
  ): Set<AccountId> {
    val audience = HashSet<AccountId>(members.size + 1)
    audience.add(owner)
    for (member in members) {
      val (accountId, masterId) = accountAndMaster(member)
      if (sessions.isPlaying(accountId, masterId)) {
        audience.add(accountId)
      }
    }
    return audience
  }

  /** The master entity whose party hears about [entityId], or null when no party should. */
  private fun partyAnchorOf(world: World, entityId: EntityId, owner: AccountId): EntityId? {
    val master = world.get(entityId, Master::class)
    if (master != null) {
      return if (sessions.isPlaying(owner, master.masterId)) entityId else null
    }

    val ownedBestia = world.get(entityId, OwnedBestia::class) ?: return null
    if (!world.has(entityId, ActivePlayer::class) || !sessions.isPlaying(owner, ownedBestia.masterId)) {
      return null
    }

    return try {
      sessions.getSelectedMasterEntityId(owner)
    } catch (_: NoActiveSessionException) {
      null
    }
  }

  companion object {
    /** What [of] reads, for a system that addresses a message with it. */
    val READS: ComponentClassSet = setOf(Account::class, Master::class, OwnedBestia::class)
  }
}
