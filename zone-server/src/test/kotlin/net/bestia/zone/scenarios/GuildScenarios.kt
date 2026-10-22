package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.guild.GuildSkillCooldownSMSG
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.aoi.EntityAudience
import net.bestia.zone.companion.query.GuildEmblemQueries
import net.bestia.zone.companion.query.GuildQueries
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.chat.net.ChatCMSG
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.guild.GuildException
import net.bestia.zone.guild.GuildExpLedger
import net.bestia.zone.guild.GuildInfoSMSG
import net.bestia.zone.guild.GuildInvitationSMSG
import net.bestia.zone.guild.GuildLeftSMSG
import net.bestia.zone.guild.GuildLogSMSG
import net.bestia.zone.guild.EmblemImage
import net.bestia.zone.guild.EmblemImageTest
import net.bestia.zone.guild.GuildService
import net.bestia.zone.guild.ecs.GuildTag
import net.bestia.zone.guild.ecs.GuildMemberBeaconComponentSMSG
import net.bestia.zone.guild.net.AnswerGuildInviteCMSG
import net.bestia.zone.guild.net.CreateGuildCMSG
import net.bestia.zone.guild.net.DisbandGuildCMSG
import net.bestia.zone.guild.net.InvestGuildSkillCMSG
import net.bestia.zone.guild.net.InviteToGuildCMSG
import net.bestia.zone.guild.net.KickGuildMemberCMSG
import net.bestia.zone.guild.net.LeaveGuildCMSG
import net.bestia.zone.guild.net.RequestGuildInfoCMSG
import net.bestia.zone.guild.net.RequestGuildLogCMSG
import net.bestia.zone.guild.persistence.GuildLogKind
import net.bestia.zone.guild.net.SaveGuildRankCMSG
import net.bestia.zone.guild.net.SetGuildEmblemCMSG
import net.bestia.zone.guild.net.SetGuildMotdCMSG
import net.bestia.zone.guild.skill.GuildSkillTree
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.SkillId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A guild's life from the wire, in order: player1 founds it with a Founder's Seal, player2 joins, and the last
 * test disbands it. Guild state goes out on a background job, so anything waiting for it is asserted in [await].
 */
class GuildScenarios : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var guildService: GuildService

  @Autowired
  private lateinit var ledger: GuildExpLedger

  @Autowired
  private lateinit var world: EcsWorld

  @Autowired
  private lateinit var skillTree: GuildSkillTree

  @Autowired
  private lateinit var entityAudience: EntityAudience

  @Autowired
  private lateinit var emblemQueries: GuildEmblemQueries

  @Autowired
  private lateinit var guildQueries: GuildQueries

  private val player1MasterId by lazy { testData.account1.masterIds.first() }
  private val player2MasterId by lazy { testData.account2.masterIds.first() }
  private val extensionId by lazy { assertNotNull(skillTree.find(SkillId.GUILD_EXTENSION)).skillId }

  private var guildId: Long = 0
  private var memberRankId: Long = 0

  @AfterAll
  fun disbandWhateverIsLeft() {
    // The Spring context is shared with every other scenario: a failure halfway must not leave a guild behind.
    listOf(clientPlayer1 to GUILD_NAME, clientPlayer3 to THIRD_GUILD_NAME).forEach { (client, name) ->
      try {
        guildService.disband(client.connectedPlayerId, name)
      } catch (_: GuildException) {
        // Never founded, or already gone.
      }
    }
  }

  @Test
  @Order(1)
  fun `outside a guild the guild info is empty rather than an error`() {
    clientPlayer1.sendMessage(RequestGuildInfoCMSG(clientPlayer1.connectedPlayerId))

    assertEquals(0L, clientPlayer1.getLastReceived(GuildInfoSMSG::class).guildId)
    assertNull(clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class))
  }

  @Test
  @Order(2)
  fun `founding without a seal is refused`() {
    clientPlayer1.sendMessage(CreateGuildCMSG(clientPlayer1.connectedPlayerId, GUILD_NAME))

    assertEquals(OperationErrorSMSG(OpError.REQUEST_REFUSED), clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class))
  }

  @Test
  @Order(3)
  fun `founding with a seal makes its bearer the leader`() {
    inventoryService.addItem(player1MasterId, "founders_seal", 1)

    clientPlayer1.sendMessage(CreateGuildCMSG(clientPlayer1.connectedPlayerId, GUILD_NAME))

    await {
      val info = clientPlayer1.tryGetLastReceived(GuildInfoSMSG::class)
      assertNotNull(info)
      assertEquals(GUILD_NAME, info.name)
      assertEquals(1, info.level)
      assertEquals(16, info.maxMembers)
      assertEquals(1, info.skillPoints)
      assertEquals(listOf("Guild Master", "Member"), info.ranks.map { it.name })
      assertEquals(listOf(player1MasterId), info.members.map { it.masterId })
      assertTrue(info.members.single().isLeader)
      assertEquals(player1MasterId, info.ownMasterId)

      guildId = info.guildId
      memberRankId = info.ranks.single { it.position == 1 }.rankId
    }

    assertEquals(
      GuildMembership(guildId, 5, GUILD_NAME, "Guild Master", isLeader = true, mayUseGuildSkills = true),
      membershipOf(clientPlayer1.connectedPlayerId)?.copy(members = emptySet()),
    )
  }

  @Test
  @Order(4)
  fun `founding a second guild is refused`() {
    inventoryService.addItem(player1MasterId, "founders_seal", 1)

    clientPlayer1.sendMessage(CreateGuildCMSG(clientPlayer1.connectedPlayerId, "Another"))

    assertEquals(OperationErrorSMSG(OpError.REQUEST_REFUSED), clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class))
  }

  /** The seal and the guild are one transaction: a refusal after taking the seal gives it back. */
  @Test
  @Order(5)
  fun `a taken name keeps the seal`() {
    inventoryService.addItem(testData.account3.masterIds.first(), "founders_seal", 1)

    clientPlayer3.sendMessage(CreateGuildCMSG(clientPlayer3.connectedPlayerId, GUILD_NAME))
    assertEquals(OperationErrorSMSG(OpError.GUILD_NAME_TAKEN), clientPlayer3.tryGetLastReceived(OperationErrorSMSG::class))

    clientPlayer3.sendMessage(CreateGuildCMSG(clientPlayer3.connectedPlayerId, THIRD_GUILD_NAME))
    await { assertEquals(THIRD_GUILD_NAME, clientPlayer3.tryGetLastReceived(GuildInfoSMSG::class)?.name) }

    clientPlayer3.sendMessage(DisbandGuildCMSG(clientPlayer3.connectedPlayerId, THIRD_GUILD_NAME))
    await { assertNotNull(clientPlayer3.tryGetLastReceived(GuildLeftSMSG::class)) }
  }

  @Test
  @Order(6)
  fun `a declined invitation tells the inviter who declined`() {
    clientPlayer1.sendMessage(InviteToGuildCMSG(clientPlayer1.connectedPlayerId, "player2"))
    assertEquals(
      OperationSuccessSMSG(OpSuccess.GUILD_INVITATION_SENT),
      clientPlayer1.tryGetLastReceived(OperationSuccessSMSG::class)
    )

    val invitation = clientPlayer2.getLastReceived(GuildInvitationSMSG::class)
    assertEquals(GUILD_NAME, invitation.guildName)
    assertEquals("player1", invitation.invitedByMaster)

    clientPlayer2.sendMessage(AnswerGuildInviteCMSG(clientPlayer2.connectedPlayerId, invitation.invitationId, accept = false))

    assertEquals(
      OperationErrorSMSG(OpError.GUILD_INVITE_DECLINED, listOf("player2")),
      clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class)
    )
  }

  @Test
  @Order(7)
  fun `an accepted invitation seats the newcomer on the lowest rank and tells everyone`() {
    clientPlayer1.sendMessage(InviteToGuildCMSG(clientPlayer1.connectedPlayerId, "player2"))
    val invitation = clientPlayer2.getLastReceived(GuildInvitationSMSG::class)

    clientPlayer2.sendMessage(AnswerGuildInviteCMSG(clientPlayer2.connectedPlayerId, invitation.invitationId, accept = true))

    await {
      listOf(clientPlayer1, clientPlayer2).forEach { client ->
        val info = client.tryGetLastReceived(GuildInfoSMSG::class)
        assertEquals(2, info?.members?.size)
        assertEquals(memberRankId, info?.members?.single { it.masterId == player2MasterId }?.rankId)
      }
      assertEquals(GuildMembership(guildId, 5, GUILD_NAME, "Member"), membershipOf(clientPlayer2.connectedPlayerId)?.copy(members = emptySet()))
      val roster = setOf(
        GuildMembership.Member(clientPlayer1.connectedPlayerId, player1MasterId),
        GuildMembership.Member(clientPlayer2.connectedPlayerId, player2MasterId),
      )
      assertEquals(roster, membershipOf(clientPlayer1.connectedPlayerId)?.members, "the leader's roster gains the newcomer")
      assertEquals(roster, membershipOf(clientPlayer2.connectedPlayerId)?.members)
    }
  }

  @Test
  @Order(8)
  fun `a member without the permission cannot invite`() {
    clientPlayer2.sendMessage(InviteToGuildCMSG(clientPlayer2.connectedPlayerId, "player3"))

    assertEquals(OperationErrorSMSG(OpError.REQUEST_REFUSED), clientPlayer2.tryGetLastReceived(OperationErrorSMSG::class))
    assertNull(clientPlayer3.tryGetLastReceived(GuildInvitationSMSG::class))
  }

  @Test
  @Order(9)
  fun `the leader cannot be kicked`() {
    clientPlayer2.sendMessage(KickGuildMemberCMSG(clientPlayer2.connectedPlayerId, player1MasterId))

    assertEquals(OperationErrorSMSG(OpError.REQUEST_REFUSED), clientPlayer2.tryGetLastReceived(OperationErrorSMSG::class))
  }

  @Test
  @Order(10)
  fun `a rank's new tax reaches its members' masters`() {
    clientPlayer1.sendMessage(
      SaveGuildRankCMSG(clientPlayer1.connectedPlayerId, memberRankId, "Recruit", 20, false, false, false, false, false)
    )

    await {
      assertEquals("Recruit", clientPlayer2.tryGetLastReceived(GuildInfoSMSG::class)?.ranks?.single { it.rankId == memberRankId }?.name)
      assertEquals(GuildMembership(guildId, 20, GUILD_NAME, "Recruit"), membershipOf(clientPlayer2.connectedPlayerId)?.copy(members = emptySet()))
    }
  }

  @Test
  @Order(11)
  fun `a tax outside the bounds is refused`() {
    clientPlayer1.sendMessage(
      SaveGuildRankCMSG(clientPlayer1.connectedPlayerId, memberRankId, "Recruit", 51, false, false, false, false, false)
    )

    assertEquals(OperationErrorSMSG(OpError.REQUEST_REFUSED), clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class))
  }

  @Test
  @Order(12)
  fun `guild chat reaches every member being played`() {
    clientPlayer1.sendMessage(ChatCMSG(clientPlayer1.connectedPlayerId, ChatType.GUILD, "for the guild"))

    assertEquals(
      ChatSMSG(type = ChatType.GUILD, text = "for the guild", senderUsername = "player1"),
      clientPlayer2.tryGetLastReceived(ChatSMSG::class)
    )
    assertNull(clientPlayer3.tryGetLastReceived(ChatSMSG::class))
  }

  @Test
  @Order(13)
  fun `investing the founding skill point extends the guild by four seats`() {
    clientPlayer1.sendMessage(InvestGuildSkillCMSG(clientPlayer1.connectedPlayerId, extensionId))

    await {
      val info = clientPlayer1.tryGetLastReceived(GuildInfoSMSG::class)
      assertEquals(1, info?.skills?.single { it.skillId == extensionId }?.level)
      assertEquals(20, info?.maxMembers)
      assertEquals(0, info?.skillPoints)
      assertEquals(1, membershipOf(clientPlayer2.connectedPlayerId)?.skillLevel(SkillId.GUILD_EXTENSION.name))
    }

    clientPlayer1.sendMessage(InvestGuildSkillCMSG(clientPlayer1.connectedPlayerId, extensionId))
    assertEquals(OperationErrorSMSG(OpError.REQUEST_REFUSED), clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class))
  }

  @Test
  @Order(14)
  fun `taxed EXP levels the guild once flushed`() {
    ledger.add(guildId, player2MasterId, 5_000)
    ledger.flush()

    await {
      val info = clientPlayer1.tryGetLastReceived(GuildInfoSMSG::class)
      assertEquals(2, info?.level)
      assertEquals(1_000L, info?.exp)
      assertEquals(5_000L, info?.members?.single { it.masterId == player2MasterId }?.expContributed)
      assertEquals(1, info?.skillPoints, "a level brings a skill point")
    }

    val approvalId = assertNotNull(skillTree.find(SkillId.OFFICIAL_GUILD_APPROVAL)).skillId
    clientPlayer1.sendMessage(InvestGuildSkillCMSG(clientPlayer1.connectedPlayerId, approvalId))
    assertEquals(
      OperationErrorSMSG(OpError.REQUEST_REFUSED),
      clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class),
      "Approval needs the Guild Emblem first",
    )
  }

  @Test
  @Order(15)
  fun `the leader casts Battle Orders for the guild, which then recovers for everyone`() {
    ledger.add(guildId, player2MasterId, 30_000)
    ledger.flush()
    await { assertEquals(4, clientPlayer1.tryGetLastReceived(GuildInfoSMSG::class)?.level) }

    val battleOrdersId = assertNotNull(skillTree.find(SkillId.BATTLE_ORDERS)).skillId
    clientPlayer1.sendMessage(InvestGuildSkillCMSG(clientPlayer1.connectedPlayerId, extensionId))
    clientPlayer1.sendMessage(InvestGuildSkillCMSG(clientPlayer1.connectedPlayerId, battleOrdersId))
    await { assertEquals(1, membershipOf(clientPlayer1.connectedPlayerId)?.skillLevel(SkillId.BATTLE_ORDERS.name)) }

    val leader = connectionInfoService.getActiveEntityId(clientPlayer1.connectedPlayerId)
    castBattleOrders(battleOrdersId, leader)

    await {
      val effects = world.read { get(leader, StatusEffects::class) }
      assertTrue(effects?.hasEffect(StatusEffectId.BATTLE_ORDERS.id) == true)
      assertEquals(
        GuildSkillCooldownSMSG(battleOrdersId, 180_000),
        clientPlayer2.tryGetLastReceived(GuildSkillCooldownSMSG::class),
      )
    }

    castBattleOrders(battleOrdersId, leader)
    await {
      assertEquals(
        OperationErrorSMSG(OpError.GUILD_SKILL_COOLDOWN),
        clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class),
      )
    }
  }

  @Test
  @Order(16)
  fun `members hear each other's beacon wherever they stand, and nobody else does`() {
    val player2Master = connectionInfoService.getSelectedMasterEntityId(clientPlayer2.connectedPlayerId)

    await {
      val beacon = clientPlayer1.tryGetLastReceivedFor(GuildMemberBeaconComponentSMSG::class, player2Master)
      assertEquals(world.read { get(player2Master, Health::class)?.max }, beacon?.hpMax)
    }
    assertEquals(
      setOf(clientPlayer1.connectedPlayerId, clientPlayer2.connectedPlayerId),
      world.read { entityAudience.ownerAndGuild(this, player2Master) },
      "a guild hears its members at any distance",
    )
    assertFalse(clientPlayer3.receivedAny(GuildMemberBeaconComponentSMSG::class) { true })
  }

  @Test
  @Order(17)
  fun `the message of the day is set by a rank allowed to, and reaches every member`() {
    clientPlayer2.sendMessage(SetGuildMotdCMSG(clientPlayer2.connectedPlayerId, "Mine now", ""))
    assertEquals(OperationErrorSMSG(OpError.REQUEST_REFUSED), clientPlayer2.tryGetLastReceived(OperationErrorSMSG::class))

    clientPlayer1.sendMessage(SetGuildMotdCMSG(clientPlayer1.connectedPlayerId, "Raid tonight", "Meet\u202E at the gate."))

    await {
      val info = clientPlayer2.tryGetLastReceived(GuildInfoSMSG::class)
      assertEquals("Raid tonight", info?.motdTitle)
      assertEquals("Meet at the gate.", info?.motdBody, "a direction override must not reach other members")
    }
  }

  @Test
  @Order(18)
  fun `a member who leaves is told, and the rest get the guild without them`() {
    clientPlayer2.sendMessage(LeaveGuildCMSG(clientPlayer2.connectedPlayerId))

    await {
      assertEquals(GuildLeftSMSG(guildId, GuildLeftSMSG.Reason.LEFT), clientPlayer2.tryGetLastReceived(GuildLeftSMSG::class))
      assertEquals(1, clientPlayer1.tryGetLastReceived(GuildInfoSMSG::class)?.members?.size)
      assertNull(membershipOf(clientPlayer2.connectedPlayerId))
    }
  }

  @Test
  @Order(19)
  fun `the log tells members what happened, newest first, and is for members only`() {
    clientPlayer1.sendMessage(RequestGuildLogCMSG(clientPlayer1.connectedPlayerId))

    val entries = clientPlayer1.getLastReceived(GuildLogSMSG::class).entries
    assertEquals(GuildLogKind.LEFT to "player2", entries.first().let { it.kind to it.actorName })
    assertEquals(GuildLogKind.FOUNDED to "player1", entries.last().let { it.kind to it.actorName })
    assertTrue(entries.any { it.kind == GuildLogKind.RANK_EDITED && it.detail == "Recruit" })
    assertTrue(entries.any { it.kind == GuildLogKind.SKILL_LEARNED && it.detail == extensionId.toString() })

    clientPlayer3.sendMessage(RequestGuildLogCMSG(clientPlayer3.connectedPlayerId))
    assertEquals(OperationErrorSMSG(OpError.GUILD_NOT_A_MEMBER), clientPlayer3.tryGetLastReceived(OperationErrorSMSG::class))
  }

  @Test
  @Order(20)
  fun `the leader sets an emblem once the guild learned Guild Emblem, and members wear it`() {
    val emblemSkillId = assertNotNull(skillTree.find(SkillId.GUILD_EMBLEM)).skillId
    val png = EmblemImageTest.png(EmblemImage.SIZE, EmblemImage.SIZE)

    clientPlayer1.sendMessage(SetGuildEmblemCMSG(clientPlayer1.connectedPlayerId, png))
    assertEquals(OperationErrorSMSG(OpError.REQUEST_REFUSED), clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class))

    clientPlayer1.sendMessage(InvestGuildSkillCMSG(clientPlayer1.connectedPlayerId, emblemSkillId))
    await { assertEquals(1, membershipOf(clientPlayer1.connectedPlayerId)?.skillLevel(SkillId.GUILD_EMBLEM.name)) }

    clientPlayer1.sendMessage(SetGuildEmblemCMSG(clientPlayer1.connectedPlayerId, png))

    val leader = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)
    var hash = ""
    await {
      hash = assertNotNull(clientPlayer1.tryGetLastReceived(GuildInfoSMSG::class)?.emblemHash)
      assertEquals(64, hash.length)
      assertEquals(hash, world.read { get(leader, GuildTag::class)?.emblemHash })
    }

    val served = emblemQueries.emblem("emblem", ZoneQuery.GuildEmblem(hash))
    assertEquals(BusReply.Status.OK, served.status)
    assertEquals(EmblemImage.SIZE, ImageIO.read(served.body.inputStream()).width, "the api-service gets the stored PNG")

    val forApp = assertNotNull(guildQueries.guild(clientPlayer1.connectedPlayerId, player1MasterId)?.guild)
    assertEquals(hash, forApp.emblemHash)
    assertEquals("Raid tonight", forApp.motdTitle)
    assertEquals(listOf("player1" to "Guild Master"), forApp.members.map { it.name to it.rankTitle })
    assertNull(guildQueries.guild(clientPlayer3.connectedPlayerId, player1MasterId), "not player3's master")
  }

  @Test
  @Order(21)
  fun `disbanding needs the guild's name typed`() {
    clientPlayer1.sendMessage(DisbandGuildCMSG(clientPlayer1.connectedPlayerId, "wrong name"))
    assertEquals(OperationErrorSMSG(OpError.REQUEST_REFUSED), clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class))

    clientPlayer1.sendMessage(DisbandGuildCMSG(clientPlayer1.connectedPlayerId, GUILD_NAME))

    await {
      assertEquals(GuildLeftSMSG(guildId, GuildLeftSMSG.Reason.DISBANDED), clientPlayer1.tryGetLastReceived(GuildLeftSMSG::class))
      assertNull(membershipOf(clientPlayer1.connectedPlayerId))
    }
  }

  private fun castBattleOrders(battleOrdersId: Long, leader: Long) {
    clientPlayer1.sendMessage(
      ActivateSkillCMSG(clientPlayer1.connectedPlayerId, battleOrdersId, 1, Vec3L.ZERO, targetEntityId = leader)
    )
  }

  private fun membershipOf(accountId: Long): GuildMembership? {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(accountId)

    return world.read { get(masterEntityId, GuildMembership::class) }
  }

  private companion object {
    const val GUILD_NAME = "Testguild"
    const val THIRD_GUILD_NAME = "Thirdguild"
  }
}
