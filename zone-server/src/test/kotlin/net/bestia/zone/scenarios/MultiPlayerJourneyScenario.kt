package net.bestia.zone.scenarios

import net.bestia.zone.account.master.AvailableMasterSMSG
import net.bestia.zone.account.master.CreateMasterCMSG
import net.bestia.zone.account.master.GetMasterCMSG
import net.bestia.zone.account.master.MasterCreatedSMSG
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.SelectMasterCMSG
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.account.master.skill.InvestSkillPointCMSG
import net.bestia.zone.battle.ActivateSkillCMSG
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.chat.ChatCMSG
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.ecs.battle.effects.StatusEffectsComponentSMSG
import net.bestia.zone.ecs.battle.level.LevelComponentSMSG
import net.bestia.zone.ecs.battle.status.SkillPointsComponentSMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.item.InventoryComponentSMSG
import net.bestia.zone.ecs.item.ItemVisualComponentSMSG
import net.bestia.zone.ecs.logout.LogoutIntentComponentSMSG
import net.bestia.zone.ecs.logout.RequestLogoutCMSG
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.PositionSMSG
import net.bestia.zone.entity.MoveActiveEntityCMSG
import net.bestia.zone.entity.VanishEntitySMSG
import net.bestia.zone.extensions.test
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.DropItemCMSG
import net.bestia.zone.item.loot.LootItemCMSG
import net.bestia.zone.item.loot.LootItemEntityFactory
import net.bestia.zone.party.AcceptPartyInviteCMSG
import net.bestia.zone.party.CreatePartyCMSG
import net.bestia.zone.party.DisbandPartySMSG
import net.bestia.zone.party.LeavePartyCMSG
import net.bestia.zone.party.PartyInfoSMSG
import net.bestia.zone.party.PartyInvitationCreatedSMSG
import net.bestia.zone.party.PartyInvitationSMSG
import net.bestia.zone.party.RequestDisbandPartyCMSG
import net.bestia.zone.party.RequestPartyInvitationCMSG
import net.bestia.zone.skill.GetSkillsCMSG
import net.bestia.zone.skill.SkillListSMSG
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Drives two simulated clients through a full player session end to end - login, master
 * creation/selection, movement, public/whisper chat, the party lifecycle, gaining levels and
 * skill points, a logout/relogin persistence round trip, self-cast heal and buff skills, and an
 * inventory loot/drop round trip - to catch regressions where these systems silently stop
 * talking to each other.
 *
 * Uses the same in-process [BestiaNoSocketScenario]/[net.bestia.zone.mocks.GameClientMock]
 * harness as every other scenario test (calls the handler pipeline directly, skipping the real
 * TCP socket and protobuf wire).
 */
@TestPropertySource(properties = ["world.logout-protection-seconds=1.5"])
class MultiPlayerJourneyScenario : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var lootItemEntityFactory: LootItemEntityFactory

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var masterRepository: MasterRepository

  private var newMasterId: Long = 0
  private val newMasterName = "journeyM1"

  private var partyId: Long = 0
  private var pendingInvitationId: Long = 0

  private var levelAfterExp: Int = 0
  private var skillPointsAfterInvestment: Int = 0

  private var groundItemEntityId: Long = 0

  companion object {
    private const val DIVINE_PROTECTION_ID = 2L
    private const val HEAL_ID = 4L
    private const val BLESSING_ID = 1L
    private const val BLESSING_EFFECT_ID = 5L
    private const val APPLE_ITEM_ID = 1L
  }

  @Test
  @Order(1)
  fun `login, create a new master and select it, second player connects`() {
    clientPlayer1.connect()

    clientPlayer1.sendMessage(CreateMasterCMSG.test(clientPlayer1.connectedPlayerId, newMasterName))
    await {
      clientPlayer1.getLastReceived(MasterCreatedSMSG::class)
    }

    clientPlayer1.sendMessage(GetMasterCMSG(clientPlayer1.connectedPlayerId))
    await {
      val masterList = clientPlayer1.getLastReceived(AvailableMasterSMSG::class)
      val created = masterList.master.firstOrNull { it.name == newMasterName }
      assertNotNull(created)
      newMasterId = created.id
    }

    clientPlayer1.sendMessage(SelectMasterCMSG(clientPlayer1.connectedPlayerId, newMasterId))
    await {
      assertEquals(newMasterId, connectionInfoService.getMasterId(clientPlayer1.connectedPlayerId))
    }

    clientPlayer2.connect(testData.account2.masterIds.first())
  }

  @Test
  @Order(2)
  fun `walking around updates position`() {
    clientPlayer1.sendMessage(
      MoveActiveEntityCMSG(
        playerId = clientPlayer1.connectedPlayerId,
        path = listOf(Vec3L(0, 1, 0))
      )
    )

    await {
      val pos = clientPlayer1.tryGetLastReceived(PositionSMSG::class)
      assertNotNull(pos)
    }
  }

  @Test
  @Order(3)
  fun `public chat is received by another nearby player`() {
    clientPlayer1.sendMessage(
      ChatCMSG(
        playerId = clientPlayer1.connectedPlayerId,
        type = ChatCMSG.Type.PUBLIC,
        text = "hello everyone"
      )
    )

    await {
      val chat = clientPlayer2.getLastReceived(ChatSMSG::class)
      assertEquals("hello everyone", chat.text)
      assertEquals(newMasterName, chat.senderUsername)
      assertEquals(ChatCMSG.Type.PUBLIC, chat.type)
    }
  }

  @Test
  @Order(4)
  fun `whisper chat is received by the target player`() {
    clientPlayer1.sendMessage(
      ChatCMSG(
        playerId = clientPlayer1.connectedPlayerId,
        type = ChatCMSG.Type.WHISPER,
        text = "hi player2, just you",
        targetUsername = "player2"
      )
    )

    await {
      val whisper = clientPlayer2.getLastReceived(ChatSMSG::class)
      assertEquals("hi player2, just you", whisper.text)
      assertEquals(newMasterName, whisper.senderUsername)
      assertEquals(ChatCMSG.Type.WHISPER, whisper.type)
    }
  }

  @Test
  @Order(5)
  fun `whisper chat works in the reverse direction too`() {
    clientPlayer2.sendMessage(
      ChatCMSG(
        playerId = clientPlayer2.connectedPlayerId,
        type = ChatCMSG.Type.WHISPER,
        text = "right back at you",
        targetUsername = newMasterName
      )
    )

    await {
      val whisper = clientPlayer1.getLastReceived(ChatSMSG::class)
      assertEquals("right back at you", whisper.text)
      assertEquals("player2", whisper.senderUsername)
      assertEquals(ChatCMSG.Type.WHISPER, whisper.type)
    }
  }

  @Test
  @Order(6)
  fun `creating a party and inviting player2 works`() {
    clientPlayer1.sendMessage(CreatePartyCMSG(clientPlayer1.connectedPlayerId, "journeyParty"))
    await {
      val info = clientPlayer1.getLastReceived(PartyInfoSMSG::class)
      assertEquals("journeyParty", info.partyName)
      assertEquals(1, info.member.size)
      partyId = info.partyId
    }

    clientPlayer1.sendMessage(
      RequestPartyInvitationCMSG(clientPlayer1.connectedPlayerId, clientPlayer2.connectedPlayerId)
    )

    await {
      val created = clientPlayer1.getLastReceived(PartyInvitationCreatedSMSG::class)
      assertEquals(PartyInvitationCreatedSMSG.InvitationStatus.CREATED, created.status)
    }

    await {
      val invitation = clientPlayer2.getLastReceived(PartyInvitationSMSG::class)
      assertEquals("journeyParty", invitation.partyName)
      assertEquals(newMasterName, invitation.invitedByMaster)
      pendingInvitationId = invitation.invitationId
    }
  }

  @Test
  @Order(7)
  fun `accepting the invitation adds player2 to the party`() {
    clientPlayer2.sendMessage(
      AcceptPartyInviteCMSG(playerId = clientPlayer2.connectedPlayerId, invitationId = pendingInvitationId)
    )

    await {
      val info = clientPlayer2.getLastReceived(PartyInfoSMSG::class)
      assertEquals(partyId, info.partyId)
      assertEquals(2, info.member.size)
    }
  }

  @Test
  @Order(8)
  fun `player2 leaving the party does not disband it`() {
    clientPlayer2.sendMessage(LeavePartyCMSG(clientPlayer2.connectedPlayerId))

    await {
      val info = clientPlayer1.getLastReceived(PartyInfoSMSG::class)
      assertEquals(partyId, info.partyId)
      assertEquals(1, info.member.size)
    }
  }

  @Test
  @Order(9)
  fun `the owner can destroy the remaining party`() {
    clientPlayer1.sendMessage(RequestDisbandPartyCMSG(clientPlayer1.connectedPlayerId, partyId))

    await {
      val disbanded = clientPlayer1.getLastReceived(DisbandPartySMSG::class)
      assertEquals(partyId, disbanded.partyId)
    }
  }

  @Test
  @Order(10)
  fun `gaining exp via chat command levels up and grants skill points`() {
    clientPlayer1.sendMessage(
      ChatCMSG(playerId = clientPlayer1.connectedPlayerId, type = ChatCMSG.Type.COMMAND, text = "/exp 5000")
    )

    await {
      val level = clientPlayer1.getLastReceived(LevelComponentSMSG::class)
      assertTrue(level.level > 1, "expected the master to have levelled up")
      levelAfterExp = level.level
    }

    await {
      val points = clientPlayer1.getLastReceived(SkillPointsComponentSMSG::class)
      // 5 for DIVINE_PROTECTION (BLESSING's prerequisite) + 1 for HEAL + 1 for BLESSING.
      assertTrue(points.points >= 7, "expected at least 7 skill points, got ${points.points}")
    }
  }

  @Test
  @Order(11)
  fun `investing skill points levels up heal and unlocks blessing in one batched request`() {
    clientPlayer1.sendMessage(
      InvestSkillPointCMSG(
        playerId = clientPlayer1.connectedPlayerId,
        investedPoints = listOf(
          // Satisfies BLESSING's prerequisite (DIVINE_PROTECTION level 5) within the same batch.
          InvestSkillPointCMSG.InvestedSkillPoint(attackId = DIVINE_PROTECTION_ID, amount = 5),
          InvestSkillPointCMSG.InvestedSkillPoint(attackId = HEAL_ID, amount = 1),
          InvestSkillPointCMSG.InvestedSkillPoint(attackId = BLESSING_ID, amount = 1)
        )
      )
    )

    await {
      val skills = clientPlayer1.getLastReceived(SkillListSMSG::class)
      val heal = skills.skills.firstOrNull { it.skillId == HEAL_ID }
      val blessing = skills.skills.firstOrNull { it.skillId == BLESSING_ID }
      assertNotNull(heal)
      assertNotNull(blessing)
      assertTrue(heal.level >= 1)
      assertTrue(blessing.level >= 1)
    }

    await {
      val points = clientPlayer1.getLastReceived(SkillPointsComponentSMSG::class)
      skillPointsAfterInvestment = points.points
    }

    // The spend must be durable in the master row *immediately* after investing - not only once a
    // logout snapshot runs (see @Order(12) for the relogin path). By the time the client observed
    // the decremented SkillPointsComponentSMSG, MasterSkillTreeService's transaction (LearnedSkill
    // rows + Master.skillPoints) has committed, so a crash right here could not refund the points.
    val persistedSkillPoints = masterRepository.findByIdOrThrow(newMasterId).skillPoints
    assertEquals(
      skillPointsAfterInvestment,
      persistedSkillPoints,
      "skill-point spend must be persisted to the DB in the invest transaction, not only on logout"
    )
  }

  @Test
  @Order(12)
  fun `logging out then selecting the same master again preserves level and skill points`() {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)

    clientPlayer1.sendMessage(RequestLogoutCMSG(clientPlayer1.connectedPlayerId))

    await {
      val countdown = clientPlayer1.tryGetLastReceived(LogoutIntentComponentSMSG::class)
      assertEquals(masterEntityId, countdown?.entityId)
    }

    await {
      val vanish = clientPlayer1.receivedAny(VanishEntitySMSG::class) { it.entityId == masterEntityId }
      assertTrue(vanish, "expected the master entity to vanish for its owner after logout")
    }

    // The vanish message is sent by LogoutSystem *before* it tags the entity PersistAndRemove -
    // that tag is only picked up (and the master's row actually persisted) by PersistAndRemoveSystem
    // on a later tick. Re-selecting before that has actually run would reload the master from a
    // stale DB row, so wait for the entity to be truly gone rather than just "vanished for the client".
    await {
      assertFalse(world.isAlive(masterEntityId), "expected the master entity to be fully persisted and removed")
    }

    // The account-level connection is untouched by logout, only the session's active master - so
    // re-selecting directly (rather than GameClientMock.connect(), which no-ops once "connected")
    // is exactly what a real client reconnecting and picking the same master again would send.
    clientPlayer1.sendMessage(SelectMasterCMSG(clientPlayer1.connectedPlayerId, newMasterId))

    await {
      val level = clientPlayer1.getLastReceived(LevelComponentSMSG::class)
      assertEquals(levelAfterExp, level.level, "level must survive the logout/relogin round trip")
    }

    await {
      val points = clientPlayer1.getLastReceived(SkillPointsComponentSMSG::class)
      assertEquals(
        skillPointsAfterInvestment,
        points.points,
        "remaining skill points must survive the logout/relogin round trip"
      )
    }

    clientPlayer1.sendMessage(GetSkillsCMSG(clientPlayer1.connectedPlayerId))
    await {
      val skills = clientPlayer1.getLastReceived(SkillListSMSG::class)
      assertTrue(skills.skills.any { it.skillId == HEAL_ID && it.level >= 1 })
      assertTrue(skills.skills.any { it.skillId == BLESSING_ID && it.level >= 1 })
    }
  }

  @Test
  @Order(13)
  fun `activating heal on self executes without error`() {
    val activeEntityId = connectionInfoService.getActiveEntityId(clientPlayer1.connectedPlayerId)

    clientPlayer1.sendMessage(
      ActivateSkillCMSG(
        playerId = clientPlayer1.connectedPlayerId,
        attackId = HEAL_ID,
        skillLevel = 1,
        targetPosition = Vec3L.ZERO,
        targetEntityId = activeEntityId
      )
    )

    await {
      val damage = clientPlayer1.getLastReceived(DamageEntitySMSG::class)
      assertEquals(DamageEntitySMSG.DamageType.HEAL, damage.type)
      assertEquals(activeEntityId, damage.entityId)
    }
  }

  @Test
  @Order(14)
  fun `activating blessing on self shows a buff that later disappears`() {
    val activeEntityId = connectionInfoService.getActiveEntityId(clientPlayer1.connectedPlayerId)

    clientPlayer1.sendMessage(
      ActivateSkillCMSG(
        playerId = clientPlayer1.connectedPlayerId,
        attackId = BLESSING_ID,
        skillLevel = 1,
        targetPosition = Vec3L.ZERO,
        targetEntityId = activeEntityId
      )
    )

    await {
      val effects = clientPlayer1.getLastReceived(StatusEffectsComponentSMSG::class)
      assertTrue(effects.effects.any { it.effectId == BLESSING_EFFECT_ID })
    }

    // Kept in the same test as the appearance check (rather than a separate @Test) so the
    // per-test message-buffer clear in between two tests can't wipe the expiry evidence if it
    // arrives faster than the next test method starts polling.
    // status_effects.yml gives BLESSING a short 4s duration precisely so this doesn't need to
    // block for a real minute; StatusEffectDurationSystem ticks on a real 1s schedule, so this is
    // a genuine (bounded) wall-clock wait, not a simulated one.
    await {
      val effects = clientPlayer1.getLastReceived(StatusEffectsComponentSMSG::class)
      assertFalse(effects.effects.any { it.effectId == BLESSING_EFFECT_ID })
    }
  }

  @Test
  @Order(16)
  fun `looting a ground item adds it to the inventory`() {
    val activeEntityId = connectionInfoService.getActiveEntityId(clientPlayer1.connectedPlayerId)
    val currentPos = world.read { get(activeEntityId, Position::class)?.toVec3L() } ?: Vec3L.ZERO

    groundItemEntityId = lootItemEntityFactory.createLootEntity(
      world = world,
      itemId = APPLE_ITEM_ID,
      amount = 1,
      pos = currentPos
    )

    clientPlayer1.sendMessage(LootItemCMSG(clientPlayer1.connectedPlayerId, targetEntityId = groundItemEntityId))

    await {
      val inventory = clientPlayer1.getLastReceived(InventoryComponentSMSG::class)
      assertEquals(activeEntityId, inventory.entityId)
      assertTrue(inventory.items.any { it.itemId == APPLE_ITEM_ID.toInt() && it.amount >= 1 })
    }
  }

  @Test
  @Order(17)
  fun `dropping and re-looting the item round trips through the inventory`() {
    clientPlayer1.sendMessage(DropItemCMSG(clientPlayer1.connectedPlayerId, itemId = APPLE_ITEM_ID, amount = 1))

    var droppedEntityId: Long = 0
    await {
      val inventory = clientPlayer1.getLastReceived(InventoryComponentSMSG::class)
      assertFalse(inventory.items.any { it.itemId == APPLE_ITEM_ID.toInt() })

      val visual = clientPlayer1.getLastReceived(ItemVisualComponentSMSG::class)
      assertEquals(APPLE_ITEM_ID.toInt(), visual.itemId)
      droppedEntityId = visual.entityId
    }

    clientPlayer1.sendMessage(LootItemCMSG(clientPlayer1.connectedPlayerId, targetEntityId = droppedEntityId))

    await {
      val inventory = clientPlayer1.getLastReceived(InventoryComponentSMSG::class)
      assertTrue(inventory.items.any { it.itemId == APPLE_ITEM_ID.toInt() && it.amount >= 1 })
    }
  }
}
