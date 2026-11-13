package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ai.core.state.CommonKeys
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.mark.MarkedPrey
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.SkillCooldownService
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.net.EquipItemCMSG
import net.bestia.zone.item.net.UnequipItemCMSG
import net.bestia.zone.item.profession.Profession
import net.bestia.zone.item.profession.Professions
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
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A Hunter learns the tracker half of its tree and casts each active over the real path. */
class HunterTrackerScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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
  private lateinit var catalogue: BestiaCatalogue

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var statusEffects: StatusEffectService

  @Autowired
  private lateinit var cooldowns: SkillCooldownService

  @Autowired
  private lateinit var playerBestiaCreateOperation: PlayerBestiaCreateOperation

  private lateinit var client: GameClientMock

  private var masterId: Long = 0

  private var watcher: EntityId? = null
  private var prey: EntityId? = null

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createHunter() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "pathfinder",
        hairColor = Color.WHITE,
        skinColor = Color.WHITE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id
    // Before connecting: the live bag is read from the database when the master enters the world.
    inventoryService.addItem(masterId, IRON_SPEAR, 1)
    inventoryService.addItem(masterId, JAVELIN, 2)

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
    holdTerrain(client)
  }

  /** Every Spring context shares one world and one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    client.disconnect()
    (listOfNotNull(watcher, prey) + ownBestias()).forEach { id -> world.modify(id) { if (isAlive(id)) destroy(id) } }
  }

  @Test
  @Order(1)
  fun `the Hunter takes up a spear, and a wild bestia five tiles off sees the master`() {
    // Weapons are a Hunter's; the profession is set here as the skills are below.
    world.modify(hunter()) { id ->
      add(id, Professions(listOf(Profession.HUNTER)))
      repeat(HUNTER_LEVEL - 1) { get(id, Level::class)!!.inc() }
    }
    equip(IRON_SPEAR, EquipmentSlot.RIGHT_HAND)

    // Keeps its AI: what it notices is the point. Held in place, so it cannot wander into notice range.
    catalogue.learnset(BLOB)
    val wild = world.read { spawner.spawnMob(this, BLOB, positionOf(hunter()) + WATCHER_OFFSET, persistent = false) }
    watcher = wild
    world.modify(wild) { id -> statusEffects.applyEffect(this, id, StatusEffectId.IMMOBILIZED, 1, durationSeconds = HOLD_SECONDS) }

    await { assertEquals(hunter(), targetOf(wild)) }
  }

  @Test
  @Order(2)
  fun `the Hunter learns the tracker skills`() {
    // Looked up first: SQL is refused inside the lease.
    val levels = SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(hunter()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      add(id, IsStatusValueDirty)
    }

    await {
      val values = combat(hunter())
      assertEquals(30, values.noticeRangeCutPercent, "Camouflage 3")
      assertEquals(15, values.offRoadSpeedPercent, "Trailblazer 5")
      assertEquals(0, values.markedPreyDamagePercent, "Pack Hunt is for the bestias")
    }
  }

  @Test
  @Order(3)
  fun `the camouflaged master goes unseen five tiles off, and is seen up close`() {
    val wild = assertNotNull(watcher)

    await { assertNotEquals(hunter(), targetOf(wild)) }

    val closer = positionOf(hunter()) + Vec3L(3, 0, 0)
    world.modify(wild) { id -> get(id, Position::class)!!.apply { x = closer.x; y = closer.y; z = closer.z } }

    await { assertEquals(hunter(), targetOf(wild)) }
    world.modify(wild) { id -> destroy(id) }
    watcher = null
  }

  @Test
  @Order(4)
  fun `Hunter's Mark marks a wild bestia, and the map follows it`() {
    val at = positionOf(hunter())
    val blob = world.read { spawner.spawnMob(this, BLOB, at + Vec3L(1, 0, 0), persistent = false) }
    prey = blob
    // A blob that strikes back or wanders off breaks the casts that follow.
    world.modify(blob) { id ->
      remove(id, AiAgent::class)
      get(id, Health::class)!!.apply { max = BLOB_HEALTH; current = BLOB_HEALTH }
    }

    cast("M_HUNTERS_MARK", target = blob)

    await { assertEquals(hunter(), markerOf(blob)) }
    await { assertEquals(blob, world.read { get(hunter(), MarkedPrey::class)?.preyId }) }
  }

  @Test
  @Order(5)
  fun `Javelin hurls the spear, and the spear comes back to hand`() {
    val blob = assertNotNull(prey)
    val javelin = skillId("M_JAVELIN").toInt()
    client.clearMessages()

    cast("M_JAVELIN", target = blob)

    await {
      assertTrue(client.receivedAny(DamageEntitySMSG::class) {
        it.entityId == blob && it.attackId == javelin && it.projectileItemId == IRON_SPEAR_ID
      })
    }
    assertEquals(IRON_SPEAR_ID, worn(EquipmentSlot.RIGHT_HAND)?.itemId)
  }

  @Test
  @Order(6)
  fun `Javelin throws a javelin from the bag, and the last one stays in hand`() {
    val blob = assertNotNull(prey)
    val javelin = skillId("M_JAVELIN")
    equip(JAVELIN, EquipmentSlot.RIGHT_HAND)
    // The throw reads the hand from the recalculated values, which follow the equip a tick later.
    await { assertEquals(WeaponType.THROWN, combat(hunter()).heldWeaponType) }
    assertEquals(1, javelinsInTheBag(), "one in hand, one in the bag")
    awaitReady(javelin)
    client.clearMessages()

    cast("M_JAVELIN", target = blob)

    await {
      assertTrue(client.receivedAny(DamageEntitySMSG::class) {
        it.entityId == blob && it.attackId == javelin.toInt() && it.projectileItemId == JAVELIN_ID
      })
    }
    assertEquals(0, javelinsInTheBag())

    awaitReady(javelin)
    client.clearMessages()
    cast("M_JAVELIN", target = blob)

    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.SKILL_NOTHING_TO_THROW, refusal.code)
    assertEquals(JAVELIN_ID, worn(EquipmentSlot.RIGHT_HAND)?.itemId)
  }

  @Test
  @Order(7)
  fun `with empty hands Javelin is refused`() {
    client.sendMessage(UnequipItemCMSG(accountId, EquipmentSlot.RIGHT_HAND))
    await { assertNull(combat(hunter()).heldWeaponType) }
    awaitReady(skillId("M_JAVELIN"))
    client.clearMessages()

    cast("M_JAVELIN", target = assertNotNull(prey))

    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.SKILL_NEEDS_SPEAR_OR_THROWN, refusal.code)
  }

  @Test
  @Order(8)
  fun `Pack Hunt makes the Hunter's bestia fiercer against the prey`() {
    playerBestiaCreateOperation.createAndSpawn(masterId, PlayerBestiaCreateData("blob", Vec3L.ZERO))

    val bestia = awaitValue { assertNotNull(ownBestias().singleOrNull()) }
    await { assertEquals(30, combat(bestia).markedPreyDamagePercent, "Pack Hunt 3") }
  }

  private fun cast(identifier: String, target: EntityId) {
    val skillId = skillId(identifier)
    world.modify(hunter()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, SKILLS.toMap().getValue(identifier), positionOf(hunter()), target))
  }

  private fun awaitReady(skillId: Long) {
    await { assertTrue(world.read { cooldowns.isReady(this, hunter(), skillId) }) }
  }

  private fun equip(identifier: String, slot: EquipmentSlot) {
    val item = world.read {
      getOrThrow(hunter(), Inventory::class).getItems().first { !it.equipped && it.itemId == itemIdOf(identifier) }
    }
    client.clearMessages()
    client.sendMessage(EquipItemCMSG(accountId, item.playerItemId, slot))
    await {
      assertEquals(item.itemId, worn(slot)?.itemId, "refused: ${client.tryGetLastReceived(OperationErrorSMSG::class)?.code}")
    }
  }

  private fun itemIdOf(identifier: String): Long {
    return when (identifier) {
      IRON_SPEAR -> IRON_SPEAR_ID
      JAVELIN -> JAVELIN_ID
      else -> error("not in the Hunter's bag: $identifier")
    }
  }

  /** The pile in the bag, without the copy in hand. */
  private fun javelinsInTheBag(): Int {
    return world.read {
      getOrThrow(hunter(), Inventory::class).getItems().filter { it.itemId == JAVELIN_ID && it.isStackable }.sumOf { it.amount }
    }
  }

  private fun targetOf(creature: EntityId): EntityId? {
    return world.read { get(creature, AiAgent::class)?.memory?.get(CommonKeys.TARGET_ID) }
  }

  private fun markerOf(entityId: EntityId): EntityId? {
    return world.read { get(entityId, StatusEffects::class)?.activeEffects?.firstOrNull { it.definitionId == StatusEffectId.HUNTERS_MARK.id }?.sourceEntityId }
  }

  private fun combat(entityId: EntityId): CombatValues {
    return world.read { getOrThrow(entityId, CombatStats::class).values }
  }

  private fun worn(slot: EquipmentSlot): Equipment.EquippedItem? {
    return world.read { get(hunter(), Equipment::class)?.get(slot) }
  }

  private fun positionOf(entityId: EntityId): Vec3L {
    return world.read { getOrThrow(entityId, Position::class).toVec3L() }
  }

  private fun ownBestias(): List<EntityId> {
    return world.read { OwnedBestia.ownedBy(this, masterId) }.map { it.entityId }
  }

  private fun skillId(identifier: String): Long {
    return assertNotNull(skills.findByIdentifier(identifier)).id
  }

  private fun hunter(): EntityId {
    return connectionInfoService.getSelectedMasterEntityId(accountId)
  }

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 42L
    const val BLOB = 1L
    const val HUNTER_LEVEL = 50
    const val BLOB_HEALTH = 100_000
    const val MANA = 500

    /** Past the blob's notice range under Camouflage 3, 70% of its sight radius of 6, inside its sight. */
    val WATCHER_OFFSET = Vec3L(5, 0, 0)

    /** Longer than the whole class runs. */
    const val HOLD_SECONDS = 600.0

    const val IRON_SPEAR = "iron_spear"
    const val IRON_SPEAR_ID = 307L
    const val JAVELIN = "javelin"
    const val JAVELIN_ID = 318L

    val SKILLS = listOf(
      "M_CAMOUFLAGE" to 3, "M_TRAILBLAZER" to 5, "M_HUNTERS_MARK" to 5, "M_JAVELIN" to 5, "M_PACK_HUNT" to 3,
    )
  }
}
