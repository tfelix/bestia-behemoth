package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.casting.SkillCooldownService
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation.PlayerBestiaCreateData
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.spawn.BestiaEntitySpawner
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * An Alchemist throws potions from the bag over the real path: a health potion onto the own bestia 5 tiles away, a
 * mana potion at the own feet, and neither a loaf nor a throw at a wild blob is taken.
 */
class AlchemistScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var accountFactory: AccountFactory

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var gameClientFactory: GameClientMockFactory

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var skills: SkillRepository

  @Autowired
  private lateinit var spawner: BestiaEntitySpawner

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var cooldowns: SkillCooldownService

  @Autowired
  private lateinit var visibility: EntityVisibility

  @Autowired
  private lateinit var playerBestiaCreateOperation: PlayerBestiaCreateOperation

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private lateinit var client: GameClientMock

  private var masterId = 0L

  private var blob: EntityId? = null

  private val accountId get() = client.connectedPlayerId

  private var pitcherId = 0L

  @BeforeAll
  fun createAlchemist() {
    // Looked up first: SQL is refused inside the lease.
    pitcherId = assertNotNull(skills.findByIdentifier("M_POTION_PITCHER")).id
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "alchemist",
        hairColor = Color.GREEN,
        skinColor = Color.GREEN,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id
    // Before connecting: the live bag and bestias are read from the database when the master enters the world.
    playerBestiaCreateOperation.createAndSpawn(masterId, PlayerBestiaCreateData("blob", Vec3L.ZERO))
    inventoryService.addItem(masterId, "small_health_potion", 2)
    inventoryService.addItem(masterId, "small_mana_potion", 1)
    inventoryService.addItem(masterId, "bread", 1)

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
    holdTerrain(client)
  }

  /** Every Spring context shares one world and one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    client.disconnect()
    (listOfNotNull(blob) + ownBestia()).forEach { id -> world.modify(id) { if (isAlive(id)) destroy(id) } }
  }

  @Test
  @Order(1)
  fun `a health potion thrown at the own bestia heals it 50 percent more at level 5`() {
    world.modify(alchemist()) { id -> get(id, KnownSkills::class)!!.learnOrUpdate(pitcherId, LEVEL) }
    val bestia = placeOwnBestia(Vec3L(0, 5, 0))
    world.modify(bestia) { id -> get(id, Health::class)!!.current = 1 }
    client.clearMessages()

    cast(bestia, SMALL_HEALTH_POTION)

    // 45 at 150 percent.
    await { assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == bestia && it.damage == 67 }) }
    assertTrue(world.read { getOrThrow(bestia, Health::class).current } > 1)
    assertEquals(1, liveAmount(SMALL_HEALTH_POTION))
    await { assertEquals(1, durableAmount(SMALL_HEALTH_POTION)) }
  }

  @Test
  @Order(2)
  fun `a mana potion thrown at the own feet fills the Alchemist's mana`() {
    awaitReady()
    world.modify(alchemist()) { id -> get(id, Mana::class)!!.current = 1 }

    cast(alchemist(), SMALL_MANA_POTION)

    await { assertEquals(0, liveAmount(SMALL_MANA_POTION)) }
    // The 1 the cast cost, then 45 at 150 percent, as far as the pool holds.
    val (current, max) = world.read { getOrThrow(alchemist(), Mana::class).let { it.current to it.max } }
    assertTrue(current >= minOf(67, max), "mana $current of $max")
    await { assertEquals(0, durableAmount(SMALL_MANA_POTION)) }
  }

  @Test
  @Order(3)
  fun `a loaf is no potion and a wild blob is no ally, and neither costs a thing`() {
    val bestia = ownBestia().single()
    val wild = spawnBlob(Vec3L(1, 0, 0))

    assertEquals(OpError.SKILL_INVALID_CHOICE, refusalOf(bestia, BREAD))
    assertEquals(OpError.SKILL_NEEDS_ALLY, refusalOf(wild, SMALL_HEALTH_POTION))
    assertEquals(1, liveAmount(BREAD))
    assertEquals(1, liveAmount(SMALL_HEALTH_POTION))
  }

  private fun refusalOf(target: EntityId, itemId: Long): OpError {
    awaitReady()
    client.clearMessages()
    cast(target, itemId)

    return assertNotNull(awaitValue { client.tryGetLastReceived(OperationErrorSMSG::class)!! }).code
  }

  /** The Alchemist's own bestia at [offset] from the Alchemist, standing still. */
  private fun placeOwnBestia(offset: Vec3L): EntityId {
    val bestia = ownBestia().single()
    val at = positionOf(alchemist()) + offset
    // A bestia that follows its own orders would walk back to its master.
    world.modify(bestia) { id ->
      remove(id, AiAgent::class)
      get(id, Position::class)!!.apply { x = at.x; y = at.y; z = at.z }
    }

    return bestia
  }

  /** A wild blob next to the Alchemist that neither fights back nor wanders. */
  private fun spawnBlob(offset: Vec3L): EntityId {
    val at = positionOf(alchemist()) + offset
    val spawned = world.read { spawner.spawnMob(this, BLOB, at, persistent = false) }
    blob = spawned
    world.modify(spawned) { id -> remove(id, AiAgent::class) }
    // The client can only aim at what it was told about.
    await { assertTrue(accountId in visibility.observersOf(spawned)) }

    return spawned
  }

  private fun cast(target: EntityId, itemId: Long) {
    world.modify(alchemist()) { id -> get(id, Mana::class)!!.apply { if (current < 1) current = 1 } }
    client.sendMessage(ActivateSkillCMSG(accountId, pitcherId, LEVEL, positionOf(target), target, listOf(itemId)))
  }

  private fun awaitReady() {
    await { assertTrue(world.read { cooldowns.isReady(this, alchemist(), pitcherId) }) }
  }

  private fun liveAmount(itemId: Long): Int {
    return world.read {
      getOrThrow(alchemist(), Inventory::class).getItems().filter { it.itemId == itemId }.sumOf { it.amount }
    }
  }

  private fun durableAmount(itemId: Long): Int {
    return TransactionTemplate(transactionManager).execute {
      masterRepository.findByIdOrThrow(masterId).container.slots
        .filter { it.isFree && it.template.id == itemId }
        .sumOf { it.amount }
    }!!
  }

  private fun positionOf(entityId: EntityId): Vec3L {
    return world.read { getOrThrow(entityId, Position::class).toVec3L() }
  }

  private fun ownBestia(): List<EntityId> {
    return world.read { OwnedBestia.ownedBy(this, masterId) }.map { it.entityId }
  }

  private fun alchemist(): EntityId {
    return connectionInfoService.getSelectedMasterEntityId(accountId)
  }

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 46L
    const val LEVEL = 5
    const val BLOB = 1L

    const val SMALL_HEALTH_POTION = 3L
    const val SMALL_MANA_POTION = 33L
    const val BREAD = 30L
  }
}
