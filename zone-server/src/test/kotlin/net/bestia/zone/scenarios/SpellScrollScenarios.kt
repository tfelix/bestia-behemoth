package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.account.persistence.LearnedSkillRepository
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.InscribedSpell
import net.bestia.zone.casting.SpellScrollOdds
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.net.UseItemCMSG
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.persistence.findByIdentifierOrThrow
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.master.bestia.OwnedBestiasSMSG
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation.PlayerBestiaCreateData
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.postal.persistence.ParcelRepository
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.ConnectionInfoService.PlayerEntity
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.spellscroll.InscriptionOptionsSMSG
import net.bestia.zone.spellscroll.SpellScrollScript
import net.bestia.zone.spellscroll.SpellScrollService
import net.bestia.zone.spellscroll.SpellScrollsSMSG
import net.bestia.zone.spellscroll.net.InscriptionOptionsCMSG
import net.bestia.zone.spellscroll.net.SpellScrollsCMSG
import net.bestia.zone.spellscroll.persistence.InscribedScroll
import net.bestia.zone.spellscroll.persistence.SpellScrollInscriptionRepository
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A Sage's spell scrolls over the real services and message path: writing, reading, training, using, losing. */
class SpellScrollScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var accountFactory: AccountFactory

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var playerBestiaCreateOperation: PlayerBestiaCreateOperation

  @Autowired
  private lateinit var gameClientFactory: GameClientMockFactory

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var scrolls: SpellScrollService

  @Autowired
  private lateinit var inscriptions: SpellScrollInscriptionRepository

  @Autowired
  private lateinit var itemInstances: ItemInstanceRepository

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var playerBestias: PlayerBestiaRepository

  @Autowired
  private lateinit var learnedSkills: LearnedSkillRepository

  @Autowired
  private lateinit var parcels: ParcelRepository

  @Autowired
  private lateinit var skills: SkillRepository

  @Autowired
  private lateinit var bestiaCatalogue: BestiaCatalogue

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private lateinit var client: GameClientMock

  private var masterId = 0L

  private val accountId get() = client.connectedPlayerId

  private val healId by lazy { skills.findByIdentifier("minor_heal")!!.id }

  @BeforeAll
  fun createSageWithTwoBestias() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "sage",
        hairColor = Color.BLUE,
        skinColor = Color.BLUE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id
    repeat(2) { playerBestiaCreateOperation.createAndSpawn(masterId, PlayerBestiaCreateData("blob", Vec3L.ZERO)) }

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
  }

  /** Every Spring context shares one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    client.disconnect()
    bestias().forEach { bestia -> world.modify(bestia.entityId) { id -> destroy(id) } }
    TransactionTemplate(transactionManager).execute {
      inscriptions.deleteAll()
      parcels.deleteAll(parcels.findByRecipientMasterIdOrderByLatestArrival(masterId))
    }
  }

  @Test
  @Order(1)
  fun `an inscribed attack lands on a new scroll and anyone may ask what it holds`() {
    val scrollId = inscribe(InscribedSpell(healId, level = 1, attackLevel = 1))

    assertEquals(1L, inscriptions.count())
    client.clearMessages()
    client.sendMessage(SpellScrollsCMSG(accountId, listOf(scrollId, 999_999L)))

    val answer = awaitValue { assertNotNull(client.tryGetLastReceived(SpellScrollsSMSG::class)) }
    assertEquals(listOf(InscribedScroll(scrollId, healId, 1)), answer.scrolls)
  }

  @Test
  @Order(2)
  fun `training refuses a bestia below the attack's level, then reads the scroll into its memory for good`() {
    val scrollId = heldScroll()
    val bestia = bestias().first()

    val tooYoung = world.modify(masterEntity()) { id -> scrolls.trainingRefusal(this, id, bestia.entityId, scrollId) }
    assertEquals(OpError.SPELL_LEVEL_TOO_LOW, tooYoung)

    world.modify(bestia.entityId) { id -> repeat(20) { get(id, Level::class)!!.inc() } }
    world.modify(masterEntity()) { id ->
      assertEquals(null, scrolls.trainingRefusal(this, id, bestia.entityId, scrollId))
      scrolls.teach(this, id, bestia.entityId, assertNotNull(scrolls.takeScroll(this, id, scrollId)))
    }

    assertTrue(world.read { get(bestia.entityId, KnownSkills::class)!!.knowsSkill(healId, 1) })
    await { assertNotNull(learnedSkills.findByPlayerBestiaIdAndSkillId(bestia.playerBestiaId, healId)) }
    await { assertFalse(itemInstances.existsById(scrollId)) }
    assertEquals(0L, inscriptions.count())
  }

  @Test
  @Order(3)
  fun `a scroll used from the bag casts its attack for free and is gone`() {
    val scrollId = inscribe(InscribedSpell(healId, level = 1, attackLevel = 1))
    client.clearMessages()

    client.sendMessage(UseItemCMSG(accountId, scrollId, ScriptArgs.EMPTY))

    await { assertTrue(liveScrolls().isEmpty()) }
    await { assertFalse(itemInstances.existsById(scrollId)) }
    assertEquals(0L, inscriptions.count())
  }

  @Test
  @Order(4)
  fun `Spell Training refuses a scroll the Sage does not hold before its cast bar`() {
    val trainingId = skills.findByIdentifier("M_SPELL_TRAINING")!!.id
    world.modify(masterEntity()) { id ->
      get(id, KnownSkills::class)!!.learnOrUpdate(trainingId, 1)
      get(id, Mana::class)!!.apply { max = 500; current = 500 }
    }
    client.clearMessages()

    client.sendMessage(ActivateSkillCMSG(accountId, trainingId, 1, Vec3L.ZERO, bestias().first().entityId, listOf(4242L)))

    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.SKILL_INVALID_CHOICE, refusal.code)
  }

  @Test
  @Order(5)
  fun `the Inscription picker lists each bestia's attacks with both odds`() {
    client.clearMessages()

    client.sendMessage(InscriptionOptionsCMSG(accountId))

    val options = awaitValue { assertNotNull(client.tryGetLastReceived(InscriptionOptionsSMSG::class)) }.options
    assertEquals(bestias().map { it.entityId }.toSet(), options.map { it.bestiaEntityId }.toSet())
    val tackle = options.first { it.skillId == skills.findByIdentifier("tackle")!!.id }
    assertEquals(SpellScrollOdds.mindBreakPercent(tackle.successPercent), tackle.mindBreakPercent)
    assertEquals(1, bestiaCatalogue.attackLevelOf(tackle.skillId))
  }

  @Test
  @Order(6)
  fun `a broken mind loses the bestia for good and sends its bag home`() {
    val lost = bestias().first()
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    inventoryService.grantToOwner(masterId, lost.playerBestiaId, apple, 3)
    client.clearMessages()

    world.modify(masterEntity()) { id -> scrolls.breakMind(this, id, lost.entityId) }

    assertFalse(world.read { isAlive(lost.entityId) })
    await { assertFalse(playerBestias.existsById(lost.playerBestiaId)) }
    await { assertEquals(1, assertNotNull(client.tryGetLastReceived(OwnedBestiasSMSG::class)).bestias.size) }
    assertTrue(client.receivedAny(OperationErrorSMSG::class) { it.code == OpError.SPELL_MIND_BROKEN })
    assertFalse(connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId).contains(lost))
    val posted = TransactionTemplate(transactionManager).execute {
      parcels.findByRecipientMasterIdOrderByLatestArrival(masterId).flatMap { it.container.slots }.sumOf { it.amount }
    }
    assertEquals(3, posted)
  }

  /** @return the new scroll's unique id, once it is in the bag */
  private fun inscribe(spell: InscribedSpell): Long {
    val before = liveScrolls()
    world.modify(masterEntity()) { id -> scrolls.inscribe(this, id, spell) }

    return awaitValue { (liveScrolls() - before.toSet()).single() }
  }

  private fun heldScroll(): Long = liveScrolls().single()

  private fun liveScrolls(): List<Long> {
    return world.read {
      get(masterEntity(), Inventory::class)!!.getItems().filter { it.itemId == SpellScrollScript.ITEM_ID }.map { it.uniqueId }
    }
  }

  private fun bestias(): List<PlayerEntity> {
    return world.read { OwnedBestia.ownedBy(this, masterId) }.sortedBy { it.playerBestiaId }
  }

  private fun masterEntity(): EntityId = connectionInfoService.getSelectedMasterEntityId(accountId)

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 31L
  }
}
