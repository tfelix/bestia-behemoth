package net.bestia.zone.guild

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.guild.persistence.Guild
import net.bestia.zone.guild.persistence.GuildMember
import net.bestia.zone.guild.persistence.GuildMemberRef
import net.bestia.zone.guild.persistence.GuildMemberRepository
import net.bestia.zone.guild.persistence.GuildRank
import net.bestia.zone.guild.persistence.GuildRepository
import net.bestia.zone.guild.skill.GuildSkillTree
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.persistence.afterCommit
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.DisplayName
import net.bestia.zone.util.EntityId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Founding, joining, leaving and running a guild.
 *
 * Every change takes the guild's row lock first, so the rank checks below see the guild as it is, not as an
 * officer's window last saw it. What the live world mirrors - the [GuildMembership] on a member's master,
 * the seal leaving the bag - is applied once the change has committed.
 *
 * Tells nobody: the handlers hand what changed to [GuildInfoPublisher].
 */
@Service
class GuildService(
  private val guildRepository: GuildRepository,
  private val memberRepository: GuildMemberRepository,
  private val masterRepository: MasterRepository,
  private val itemRepository: ItemRepository,
  private val inventoryService: InventoryService,
  private val sessions: ConnectionInfoService,
  private val world: WorldView,
  private val config: GuildConfig,
  private val levels: GuildLevels,
  private val invitations: GuildInvitations,
  private val ledger: GuildExpLedger,
  private val skillTree: GuildSkillTree,
  private val memberships: GuildMemberships,
) {

  data class SentInvitation(val invitedAccountId: AccountId, val invitation: GuildInvitationSMSG)

  data class Declined(val inviterAccountId: AccountId, val invitedMasterName: String)

  /** [member] is out of [guildId], which goes on. */
  data class Departed(val guildId: Long, val member: GuildMemberRef)

  data class Disbanded(val guildId: Long, val members: List<GuildMemberRef>)

  /**
   * The master [accountId] plays founds a guild named [rawName], spending one Founder's Seal from its bag, and
   * leads it. A taken name rolls the whole transaction back, so the seal is kept. @return the new guild
   */
  @Transactional
  fun found(accountId: AccountId, rawName: String): Long {
    val masterId = playedMasterId(accountId)
    val name = DisplayName.normalizeOrNull(rawName, config.nameMaxLength) ?: throw GuildInvalidNameException()

    if (memberRepository.findGuildIdOfMaster(masterId) != null) {
      throw GuildRequestRefused("master $masterId is already in a guild")
    }

    val sealId = itemRepository.findByIdentifier(FOUNDERS_SEAL)?.id
      ?: throw GuildRequestRefused("items.yml has no $FOUNDERS_SEAL")

    // Locks the master's row; all or nothing, and part of this transaction, so a refusal below keeps the seal.
    if (!inventoryService.consumeAll(masterId, listOf(sealId to 1))) {
      throw GuildRequestRefused("master $masterId holds no $FOUNDERS_SEAL")
    }

    val master = masterRepository.findByIdOrNull(masterId)
      ?: throw GuildRequestRefused("master $masterId is gone")

    // Flushed here so a taken name fails inside the transaction, as the exception below, rather than at commit.
    val guild = try {
      guildRepository.saveAndFlush(Guild(name = name, leader = master))
    } catch (_: DataIntegrityViolationException) {
      throw GuildNameTakenException(name)
    }

    val leaderRank = GuildRank(
      guild = guild,
      position = GuildRank.LEADER_POSITION,
      name = LEADER_RANK_NAME,
      taxPercent = config.defaultTaxPercent,
      canInvite = true,
      canKick = true,
      canManageRanks = true,
      canUseGuildSkills = true,
    )
    guild.ranks.add(leaderRank)
    guild.ranks.add(GuildRank(guild = guild, position = 1, name = MEMBER_RANK_NAME, taxPercent = config.defaultTaxPercent))
    // Ranks first: the leader's member row points at one. A flush, not another save: saving a guild that
    // already has an id merges it, which would persist copies of the ranks and leave these instances transient.
    guildRepository.flush()

    val leader = GuildMember(guild = guild, master = master, rank = leaderRank)
    guild.members.add(leader)

    val membership = memberships.of(leader)
    afterCommit {
      onPlayedMaster(GuildMemberRef(accountId, masterId)) { id ->
        get(id, Inventory::class)?.mirrorRemoval(sealId, 1, 0L)
        add(id, membership)
      }
    }

    LOG.info { "Master $masterId founded guild ${guild.id} '$name'" }

    return guild.id
  }

  /** A member whose rank may invite asks the master named [targetName], which must be played and in no guild. */
  @Transactional(readOnly = true)
  fun invite(accountId: AccountId, targetName: String): SentInvitation {
    val masterId = playedMasterId(accountId)
    val guildId = memberRepository.findGuildIdOfMaster(masterId) ?: throw NotInGuildException()
    val guild = guildRepository.findByIdOrNull(guildId) ?: throw NotInGuildException()
    val actor = guild.memberOf(masterId) ?: throw NotInGuildException()

    if (!GuildRankRules.mayInvite(actor)) {
      throw GuildRequestRefused("master $masterId may not invite into guild $guildId")
    }

    val name = targetName.trim()
    val target = masterRepository.findByName(name)
      ?.takeIf { sessions.isPlaying(it.account.id, it.id) }
      ?: throw GuildTargetUnavailableException(name)

    // Covers inviting oneself too.
    if (memberRepository.findGuildIdOfMaster(target.id) != null) {
      throw GuildTargetInGuildException(target.name)
    }

    val open = invitations.open(
      guildId = guild.id,
      guildName = guild.name,
      seatsFree = levels.maxMembers(guild.extensionLevel) - guild.members.size,
      inviterAccountId = accountId,
      inviterName = actor.master.name,
      invitedAccountId = target.account.id,
      invitedMasterId = target.id,
      invitedMasterName = target.name,
    )

    return SentInvitation(target.account.id, open.toMessage())
  }

  /** The invited player joins, on the guild's lowest rank. @return the guild joined */
  @Transactional
  fun accept(accountId: AccountId, invitationId: Long): Long {
    val open = invitations.take(accountId, invitationId)

    // The invitation was for the master the account played then; switching to another one ends it.
    if (!sessions.isPlaying(accountId, open.invitedMasterId)) {
      throw GuildInvitationExpiredException()
    }

    val guild = guildRepository.findByIdForUpdate(open.guildId) ?: throw GuildInvitationExpiredException()

    if (guild.members.size >= levels.maxMembers(guild.extensionLevel)) {
      throw GuildFullException()
    }

    if (memberRepository.findGuildIdOfMaster(open.invitedMasterId) != null) {
      throw GuildRequestRefused("master ${open.invitedMasterId} joined another guild meanwhile")
    }

    val master = masterRepository.findByIdOrNull(open.invitedMasterId) ?: throw GuildInvitationExpiredException()
    val rank = guild.lowestRank
    val member = GuildMember(guild = guild, master = master, rank = rank)
    guild.members.add(member)

    refreshMembership(member)

    return guild.id
  }

  fun decline(accountId: AccountId, invitationId: Long): Declined {
    val open = invitations.take(accountId, invitationId)

    return Declined(open.inviterAccountId, open.invitedMasterName)
  }

  /** A member leaves. The leader cannot: with no transfer, the guild would be left without one. */
  @Transactional
  fun leave(accountId: AccountId): Departed {
    val (guild, actor) = lockedGuildOf(playedMasterId(accountId))

    if (actor.isLeader) {
      throw GuildRequestRefused("the leader of guild ${guild.id} cannot leave it")
    }

    return remove(guild, actor)
  }

  @Transactional
  fun kick(accountId: AccountId, targetMasterId: Long): Departed {
    val (guild, actor) = lockedGuildOf(playedMasterId(accountId))
    val target = guild.memberOf(targetMasterId)
      ?: throw GuildRequestRefused("master $targetMasterId is not in guild ${guild.id}")

    if (!GuildRankRules.mayKick(actor, target)) {
      throw GuildRequestRefused("master ${actor.master.id} may not kick master $targetMasterId")
    }

    return remove(guild, target)
  }

  private fun remove(guild: Guild, member: GuildMember): Departed {
    val ref = refOf(member)
    guild.members.remove(member)
    clearMembership(ref)

    return Departed(guild.id, ref)
  }

  /** @return the guild changed */
  @Transactional
  fun setRank(accountId: AccountId, targetMasterId: Long, rankId: Long): Long {
    val (guild, actor) = lockedGuildOf(playedMasterId(accountId))
    val target = guild.memberOf(targetMasterId)
      ?: throw GuildRequestRefused("master $targetMasterId is not in guild ${guild.id}")
    val rank = guild.rank(rankId) ?: throw GuildRequestRefused("guild ${guild.id} has no rank $rankId")

    if (!GuildRankRules.maySetRank(actor, target, rank)) {
      throw GuildRequestRefused("master ${actor.master.id} may not put master $targetMasterId on rank $rankId")
    }

    target.rank = rank
    refreshMembership(target)

    return guild.id
  }

  /**
   * Edits a rank, or adds one at the bottom when [rankId] is 0. The leader's rank keeps every permission,
   * whatever is sent. @return the guild changed
   */
  @Transactional
  fun saveRank(
    accountId: AccountId,
    rankId: Long,
    rawName: String,
    taxPercent: Int,
    canInvite: Boolean,
    canKick: Boolean,
    canManageRanks: Boolean,
    canUseGuildSkills: Boolean,
  ): Long {
    val (guild, actor) = lockedGuildOf(playedMasterId(accountId))
    val name = DisplayName.normalizeOrNull(rawName, config.rankNameMaxLength) ?: throw GuildInvalidNameException()

    if (taxPercent !in config.minTaxPercent..config.maxTaxPercent) {
      throw GuildRequestRefused("tax $taxPercent is outside ${config.minTaxPercent}..${config.maxTaxPercent}")
    }

    if (rankId == NEW_RANK) {
      if (!GuildRankRules.mayManageRanks(actor) || guild.ranks.size >= config.maxRanks) {
        throw GuildRequestRefused("master ${actor.master.id} may not add a rank to guild ${guild.id}")
      }

      val position = guild.ranks.maxOf { it.position } + 1
      val rank = GuildRank(guild, position, name, taxPercent, canInvite, canKick, canManageRanks, canUseGuildSkills)
      guild.ranks.add(rank)

      return guild.id
    }

    val rank = guild.rank(rankId) ?: throw GuildRequestRefused("guild ${guild.id} has no rank $rankId")
    if (!GuildRankRules.mayEditRank(actor, rank)) {
      throw GuildRequestRefused("master ${actor.master.id} may not edit rank $rankId")
    }

    rank.name = name
    rank.taxPercent = taxPercent
    if (!rank.isLeaderRank) {
      rank.canInvite = canInvite
      rank.canKick = canKick
      rank.canManageRanks = canManageRanks
      rank.canUseGuildSkills = canUseGuildSkills
    }

    guild.members.filter { it.rank.id == rank.id }.forEach { refreshMembership(it) }

    return guild.id
  }

  /** Removes an empty rank. The leader's rank stays, and so does the last rank below it. @return the guild */
  @Transactional
  fun deleteRank(accountId: AccountId, rankId: Long): Long {
    val (guild, actor) = lockedGuildOf(playedMasterId(accountId))
    val rank = guild.rank(rankId) ?: throw GuildRequestRefused("guild ${guild.id} has no rank $rankId")

    val allowed = !rank.isLeaderRank &&
      GuildRankRules.mayEditRank(actor, rank) &&
      guild.members.none { it.rank.id == rank.id } &&
      guild.ranks.count { !it.isLeaderRank } > 1

    if (!allowed) {
      throw GuildRequestRefused("master ${actor.master.id} may not delete rank $rankId")
    }

    guild.ranks.remove(rank)

    return guild.id
  }

  /** The leader spends a skill point on the guild skill [skillId]. @return the guild */
  @Transactional
  fun investSkill(accountId: AccountId, skillId: Long): Long {
    val (guild, actor) = lockedGuildOf(playedMasterId(accountId))
    val node = skillTree.find(skillId) ?: throw GuildRequestRefused("skill $skillId is no guild skill")

    // The window offers only what these allow, so a refusal is a stale window or no client of ours.
    val allowed = actor.isLeader &&
      levels.skillPoints(guild.level, guild.spentSkillPoints) > 0 &&
      skillTree.mayRaise(node, guild::skillLevel)

    if (!allowed) {
      throw GuildRequestRefused("master ${actor.master.id} may not raise ${node.identifier} of guild ${guild.id}")
    }

    guild.skillLevels[node.identifier] = guild.skillLevel(node.identifier) + 1
    guild.members.forEach { refreshMembership(it) }

    return guild.id
  }

  /** The leader ends the guild for everyone, confirmed by typing its name. The seal is not given back. */
  @Transactional
  fun disband(accountId: AccountId, confirmName: String): Disbanded {
    val (guild, actor) = lockedGuildOf(playedMasterId(accountId))

    if (!actor.isLeader || confirmName.trim() != guild.name) {
      throw GuildRequestRefused("master ${actor.master.id} may not disband guild ${guild.id}")
    }

    val guildId = guild.id
    val members = guild.members.map { refOf(it) }

    // Members before ranks, since every member row points at a rank; left to the cascade, the order is not ours.
    guild.members.clear()
    guildRepository.flush()
    guildRepository.delete(guild)

    members.forEach { clearMembership(it) }
    afterCommit {
      ledger.discard(guildId)
      invitations.dropFor(guildId)
    }

    LOG.info { "Guild $guildId disbanded by its leader" }

    return Disbanded(guildId, members)
  }

  /** The name of the guild [masterId] leads, or null. */
  @Transactional(readOnly = true)
  fun guildNameLedBy(masterId: Long): String? {
    return guildRepository.findNameLedBy(masterId)
  }

  /**
   * Takes a master out of its guild right before the master row is deleted. A leader is never deleted (see
   * `MasterDeletionService`), so this only ever removes a plain member. @return the guild it left, or null
   */
  @Transactional
  fun detachDeletedMaster(masterId: Long): Long? {
    val guildId = memberRepository.findGuildIdOfMaster(masterId) ?: return null
    val guild = guildRepository.findByIdForUpdate(guildId) ?: return null
    val member = guild.memberOf(masterId)?.takeUnless { it.isLeader } ?: return null

    guild.members.remove(member)
    // Deleted and flushed now: left to orphan removal at the next flush, it would run after the master's own
    // delete and trip over the foreign key.
    memberRepository.delete(member)
    memberRepository.flush()

    return guildId
  }

  private fun playedMasterId(accountId: AccountId): Long {
    return sessions.activeMasterIdOf(accountId)
      ?: throw GuildRequestRefused("account $accountId plays no master")
  }

  private data class Locked(val guild: Guild, val actor: GuildMember)

  private fun lockedGuildOf(masterId: Long): Locked {
    val guildId = memberRepository.findGuildIdOfMaster(masterId) ?: throw NotInGuildException()
    val guild = guildRepository.findByIdForUpdate(guildId) ?: throw NotInGuildException()
    val actor = guild.memberOf(masterId) ?: throw NotInGuildException()

    return Locked(guild, actor)
  }

  private fun refOf(member: GuildMember) = GuildMemberRef(member.master.account.id, member.master.id)

  private fun refreshMembership(member: GuildMember) {
    val ref = refOf(member)
    val membership = memberships.of(member)

    afterCommit {
      onPlayedMaster(ref) { id -> add(id, membership) }
    }
  }

  private fun clearMembership(ref: GuildMemberRef) {
    afterCommit {
      onPlayedMaster(ref) { id -> remove(id, GuildMembership::class) }
    }
  }

  /** Members not being played get their membership from the database when they next log in. */
  private fun onPlayedMaster(ref: GuildMemberRef, block: World.(EntityId) -> Unit) {
    if (!sessions.isPlaying(ref.accountId, ref.masterId)) {
      return
    }

    val entityId = try {
      sessions.getSelectedMasterEntityId(ref.accountId)
    } catch (_: NoActiveSessionException) {
      return
    }

    world.modify(entityId) { id -> block(id) }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** The item a guild is founded with, by its `item-db-name`. */
    const val FOUNDERS_SEAL = "founders_seal"

    /** [saveRank]'s rank id for "a new one". */
    const val NEW_RANK = 0L

    // Only the founding names; the leader renames them like any other.
    private const val LEADER_RANK_NAME = "Guild Master"
    private const val MEMBER_RANK_NAME = "Member"
  }
}
