package net.bestia.zone.guild

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.guild.persistence.Guild
import net.bestia.zone.guild.persistence.GuildMember
import net.bestia.zone.guild.persistence.GuildRank
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The leader may act on anyone but themself; everyone else only below their own rank, so a rank that manages
 * ranks cannot grant itself more or demote those above it.
 */
class GuildRankRulesTest {

  private val leaderMaster = master(1)
  private val guild = Guild(name = "Rules", leader = leaderMaster)

  private val leaderRank = rank(0, "Guild Master", invite = true, kick = true, manage = true)
  private val officerRank = rank(1, "Officer", invite = true, kick = true, manage = true)
  private val memberRank = rank(2, "Member")

  private val leader = GuildMember(guild, leaderMaster, leaderRank)
  private val officer = GuildMember(guild, master(2), officerRank)
  private val otherOfficer = GuildMember(guild, master(3), officerRank)
  private val member = GuildMember(guild, master(4), memberRank)

  @Test
  fun `the leader can never be kicked, not even by an officer with the permission`() {
    assertFalse(GuildRankRules.mayKick(officer, leader))
  }

  @Test
  fun `an officer kicks below their rank and not beside it`() {
    assertTrue(GuildRankRules.mayKick(officer, member))
    assertFalse(GuildRankRules.mayKick(officer, otherOfficer))
  }

  @Test
  fun `a member without the permission kicks nobody`() {
    assertFalse(GuildRankRules.mayKick(member, member))
    assertFalse(GuildRankRules.mayInvite(member))
  }

  @Test
  fun `nobody kicks themself`() {
    assertFalse(GuildRankRules.mayKick(leader, leader))
  }

  @Test
  fun `an officer edits only the ranks below their own`() {
    assertTrue(GuildRankRules.mayEditRank(officer, memberRank))
    assertFalse(GuildRankRules.mayEditRank(officer, officerRank), "or it could grant itself what it lacks")
    assertFalse(GuildRankRules.mayEditRank(officer, leaderRank))
  }

  @Test
  fun `the leader's rank is the leader's alone to edit`() {
    assertTrue(GuildRankRules.mayEditRank(leader, leaderRank))
    assertTrue(GuildRankRules.mayEditRank(leader, officerRank))
  }

  @Test
  fun `nobody is put on the leader's rank, and the leader is never moved`() {
    assertFalse(GuildRankRules.maySetRank(leader, member, leaderRank))
    assertFalse(GuildRankRules.maySetRank(officer, leader, memberRank))
  }

  @Test
  fun `an officer moves those below onto ranks below their own`() {
    assertTrue(GuildRankRules.maySetRank(leader, member, officerRank))
    assertFalse(GuildRankRules.maySetRank(officer, member, officerRank), "that would be a promotion to their own rank")
  }

  @Test
  fun `the leader may always use guild skills, others only by their rank`() {
    val caster = GuildMember(guild, master(5), rank(3, "Caster", useSkills = true))

    assertTrue(GuildRankRules.mayUseGuildSkills(leader))
    assertTrue(GuildRankRules.mayUseGuildSkills(caster))
    assertFalse(GuildRankRules.mayUseGuildSkills(officer))
  }

  @Test
  fun `whoever may kick may lift an expulsion`() {
    assertTrue(GuildRankRules.mayLiftExpulsion(leader))
    assertTrue(GuildRankRules.mayLiftExpulsion(officer))
    assertFalse(GuildRankRules.mayLiftExpulsion(member))
  }

  @Test
  fun `only the leader hands the guild over, and not to themself`() {
    assertTrue(GuildRankRules.mayTransferLeadership(leader, officer))
    assertFalse(GuildRankRules.mayTransferLeadership(leader, leader))
    assertFalse(GuildRankRules.mayTransferLeadership(officer, member))
  }

  @Test
  fun `the leader may always edit the message of the day, others only by their rank`() {
    val herald = GuildMember(guild, master(6), rank(4, "Herald", editMotd = true))

    assertTrue(GuildRankRules.mayEditMotd(leader))
    assertTrue(GuildRankRules.mayEditMotd(herald))
    assertFalse(GuildRankRules.mayEditMotd(officer))
  }

  private fun rank(
    position: Int,
    name: String,
    invite: Boolean = false,
    kick: Boolean = false,
    manage: Boolean = false,
    useSkills: Boolean = false,
    editMotd: Boolean = false,
  ): GuildRank {
    return GuildRank(
      guild,
      position,
      name,
      taxPercent = 5,
      canInvite = invite,
      canKick = kick,
      canManageRanks = manage,
      canUseGuildSkills = useSkills,
      canEditMotd = editMotd,
    )
  }

  private fun master(id: Long): Master = mockk(relaxed = true) {
    every { this@mockk.id } returns id
  }
}
