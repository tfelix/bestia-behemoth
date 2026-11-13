package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.mining.OreInBrush
import net.bestia.zone.item.mining.PickaxeMining
import net.bestia.zone.item.mining.ecs.MiningSwing
import net.bestia.zone.item.net.EquipItemCMSG
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.treasure.net.DigCMSG
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** A Miner swings a pickaxe at real ore in the test world. */
class MiningScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var items: ItemTemplateRegistry

  @Autowired
  private lateinit var oreSpots: OreSpots

  @Autowired
  private lateinit var oreInBrush: OreInBrush

  private lateinit var client: GameClientMock

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createMiner() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    val masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "prospector",
        hairColor = Color.WHITE,
        skinColor = Color.WHITE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id
    inventoryService.addItem(masterId, PICKAXE, 1)

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
  }

  @AfterAll
  fun leave() {
    client.disconnect()
  }

  @Test
  @Order(1)
  fun `the Miner takes a pickaxe in hand`() {
    world.modify(miner()) { id ->
      repeat(MINER_LEVEL - 1) { get(id, Level::class)!!.inc() }
      add(id, IsStatusValueDirty)
    }

    client.sendMessage(EquipItemCMSG(accountId, pickaxe(), EquipmentSlot.RIGHT_HAND))

    await { assertNotNull(world.read { get(miner(), Equipment::class)?.get(EquipmentSlot.RIGHT_HAND) }) }
  }

  @Test
  @Order(2)
  fun `an untrained miner is refused ore above level 10, and it stays in the rock`() {
    val ore = hardOre()
    standNextTo(ore)
    client.clearMessages()

    client.sendMessage(DigCMSG(accountId, ore + ABOVE))

    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.MINE_ORE_TOO_HARD, refusal.code)
    assertNull(world.read { get(miner(), MiningSwing::class) })
  }

  @Test
  @Order(3)
  fun `a trained miner breaks the rock and the ore goes into the bag`() {
    learn("M_MINING", 5)
    val ore = hardOre()
    val itemId = oreSpots.itemAt(ore)
    standNextTo(ore)

    client.sendMessage(DigCMSG(accountId, ore + ABOVE))
    val swing = awaitValue { assertNotNull(world.read { get(miner(), MiningSwing::class) }) }
    assertTrue(swing.totalSeconds >= MIN_SWING_SECONDS * 0.5f, "a ball with ore takes its time")
    hurry(swing)

    await { assertTrue(amountOf(itemId) > 0, "the ore reached the bag") }
  }

  @Test
  @Order(4)
  fun `Rich Veins gives more iron or gem pieces than the rock holds`() {
    learn("M_RICH_VEINS", 5)
    val spot = oreSpots.find(positionOf(miner())) { stack -> !stack.isCoinMetal }
    val itemId = oreSpots.itemAt(spot)
    val before = amountOf(itemId)
    var plain = 0

    // Half of all blocks pay a piece more, so a few swings are as good as certain to show one.
    for (swing in 0 until RICH_VEINS_SWINGS) {
      val at = spot + Vec3L(0, 0, 1 - swing.toLong())
      plain += world.read { oreInBrush.scan(PickaxeMining.ballAt(at)) }.filter { it.stack.itemId == itemId }.sumOf { it.stack.amount }
      standNextTo(at)
      client.sendMessage(DigCMSG(accountId, at))
      hurry(awaitValue { assertNotNull(world.read { get(miner(), MiningSwing::class) }) })
      await { assertTrue(amountOf(itemId) - before >= plain, "swing $swing paid at least what the rock held") }
      if (amountOf(itemId) - before > plain) return
    }

    fail("$RICH_VEINS_SWINGS swings paid exactly the $plain pieces in the rock, never a piece more")
  }

  /** Ore no untrained miner may break, so that both scenarios aim at the same kind of rock. */
  private fun hardOre(): Vec3L {
    return oreSpots.find(positionOf(miner())) { stack -> (items.baseLevelOf(stack.itemId) ?: 0) > UNTRAINED_CAP }
  }

  /** Inside the rock is fine: the server checks reach, not whether the miner could stand there. */
  private fun standNextTo(ore: Vec3L) {
    world.modify(miner()) { id -> get(id, Position::class)!!.apply { x = ore.x + 1; y = ore.y; z = ore.z + 1 } }
  }

  /** A real swing lasts minutes; the countdown is the system's business, not this scenario's. */
  private fun hurry(swing: MiningSwing) {
    world.modify(miner()) { id -> add(id, MiningSwing(swing.accountId, swing.ball, HURRIED_SECONDS)) }
  }

  private fun learn(identifier: String, level: Int) {
    val skillId = assertNotNull(skills.findByIdentifier(identifier)).id
    world.modify(miner()) { id -> get(id, KnownSkills::class)!!.learnOrUpdate(skillId, level) }
  }

  private fun amountOf(itemId: Long): Int {
    return world.read { getOrThrow(miner(), Inventory::class).getItems().filter { it.itemId == itemId }.sumOf { it.amount } }
  }

  private fun pickaxe(): Long {
    val pickaxeId = assertNotNull(items.idOf(PICKAXE))
    return world.read { getOrThrow(miner(), Inventory::class).getItems().first { it.itemId == pickaxeId }.playerItemId }
  }

  private fun positionOf(entityId: EntityId): Vec3L = world.read { getOrThrow(entityId, Position::class).toVec3L() }

  private fun miner(): EntityId = connectionInfoService.getSelectedMasterEntityId(accountId)

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 60L
    const val PICKAXE = "pickaxe"
    const val MINER_LEVEL = 20
    const val UNTRAINED_CAP = 10
    const val MIN_SWING_SECONDS = 30f
    const val HURRIED_SECONDS = 0.2f
    const val RICH_VEINS_SWINGS = 6

    /** The tile a click on the top of a block names: the air above it. */
    val ABOVE = Vec3L(0, 0, 1)
  }
}
