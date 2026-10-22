package net.bestia.zone.guild

import net.bestia.zone.guild.persistence.GuildMember
import net.bestia.zone.guild.persistence.GuildRank

/**
 * Who in a guild may do what to whom. Pure, so the hierarchy can be tested without a database.
 *
 * The leader may act on everyone but themself. Anybody else acts only on members and ranks *below* their own
 * rank: a rank that may manage ranks could otherwise grant itself whatever it liked, or demote whoever outranks
 * it.
 */
object GuildRankRules {

  fun mayInvite(actor: GuildMember): Boolean = actor.isLeader || actor.rank.canInvite

  fun mayKick(actor: GuildMember, target: GuildMember): Boolean {
    return (actor.isLeader || actor.rank.canKick) && outranks(actor, target)
  }

  /** Moving [target] onto [rank]. Nobody is moved onto the leader's rank, and the leader is never moved. */
  fun maySetRank(actor: GuildMember, target: GuildMember, rank: GuildRank): Boolean {
    if (rank.isLeaderRank || !mayManageRanks(actor) || !outranks(actor, target)) {
      return false
    }

    return actor.isLeader || rank.position > actor.rank.position
  }

  /** Editing [rank]'s name, tax or permissions. The leader's rank is the leader's alone to rename or tax. */
  fun mayEditRank(actor: GuildMember, rank: GuildRank): Boolean {
    if (rank.isLeaderRank) {
      return actor.isLeader
    }

    return mayManageRanks(actor) && (actor.isLeader || rank.position > actor.rank.position)
  }

  fun mayManageRanks(actor: GuildMember): Boolean = actor.isLeader || actor.rank.canManageRanks

  fun mayUseGuildSkills(member: GuildMember): Boolean {
    return member.isLeader || member.rank.canUseGuildSkills
  }

  /** The leader outranks everyone else; anyone else only those on a lower rank. Never oneself. */
  fun outranks(actor: GuildMember, target: GuildMember): Boolean {
    if (actor.master.id == target.master.id || target.isLeader) {
      return false
    }

    return actor.isLeader || target.rank.position > actor.rank.position
  }
}
