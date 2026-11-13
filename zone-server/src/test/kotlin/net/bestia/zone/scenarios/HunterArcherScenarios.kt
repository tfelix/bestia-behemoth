package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.SkillCooldownService
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.net.EquipItemCMSG
import net.bestia.zone.item.net.SetQuiverCMSG
import net.bestia.zone.item.net.UnequipItemCMSG
import net.bestia.zone.item.profession.Profession
import net.bestia.zone.item.profession.Professions
import net.bestia.zone.master.MasterFactory
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A Hunter learns the archer half of its tree and casts each active over the real path. */
class HunterArcherScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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
  private lateinit var visibility: EntityVisibility

  private lateinit var client: GameClientMock

  private var masterId: Long = 0

  private val blobs = mutableListOf<EntityId>()

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createHunter() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "longshot",
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
    inventoryService.addItem(masterId, HUNTING_BOW, 1)

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
    holdTerrain(client)
    catalogue.learnset(BLOB)
  }

  /** Every Spring context shares one world and one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    client.disconnect()
    blobs.forEach { id -> world.modify(id) { if (isAlive(id)) destroy(id) } }
  }

  @Test
  @Order(1)
  fun `the Hunter takes up a hunting bow`() {
    // Bows are a Hunter's; the profession is set here as the skills are below.
    world.modify(hunter()) { id ->
      add(id, Professions(listOf(Profession.HUNTER)))
      repeat(HUNTER_LEVEL - 1) { get(id, Level::class)!!.inc() }
    }
    // A bow needs both hands, and the starter kit fills the left one.
    client.sendMessage(UnequipItemCMSG(accountId, EquipmentSlot.LEFT_HAND))
    await { assertNull(world.read { get(hunter(), Equipment::class)?.get(EquipmentSlot.LEFT_HAND) }) }
    equip(HUNTING_BOW, HUNTING_BOW_ID)
    client.sendMessage(SetQuiverCMSG(accountId, listOf(ARROW_ID)))

    await { assertEquals(WeaponType.BOW, combat().heldWeaponType) }
    assertEquals(BOW_RANGE, combat().weapon.ranged?.range)
    assertTrue(arrowsInTheBag() > 0, "the starter arrows")
  }

  @Test
  @Order(2)
  fun `the Hunter learns the archer skills, and Owl's Eye and Vulture's Eye sharpen the aim`() {
    val before = combat()
    // Looked up first: SQL is refused inside the lease.
    val levels = SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(hunter()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      add(id, IsStatusValueDirty)
    }

    await { assertEquals(BOW_RANGE + 5, combat().weapon.ranged?.range, "Vulture's Eye 5") }
    val after = combat()
    assertEquals(before.attributes.dexterity + 5, after.attributes.dexterity, "Owl's Eye 5")
    assertEquals(before.derived.hitrate + 5 + 10, after.derived.hitrate, "5 DEX and Vulture's Eye's 10 HIT")
  }

  @Test
  @Order(3)
  fun `Double Strafe shoots two arrows at a foe and takes one`() {
    val blob = blobAt(Vec3L(1, 0, 0))
    val strafe = skillId("M_DOUBLE_STRAFE").toInt()
    val arrows = arrowsInTheBag()
    client.clearMessages()

    cast("M_DOUBLE_STRAFE", target = blob)

    await { assertEquals(2, client.countReceived(DamageEntitySMSG::class) { it.isArrowFrom(strafe, blob) }) }
    assertEquals(arrows - 1, arrowsInTheBag())
  }

  @Test
  @Order(4)
  fun `Arrow Shower rains on the foes around the spot aimed at and takes one arrow`() {
    val first = blobs.first()
    val second = blobAt(Vec3L(1, 1, 0))
    val shower = skillId("M_ARROW_SHOWER").toInt()
    val arrows = arrowsInTheBag()
    client.clearMessages()

    cast("M_ARROW_SHOWER", aimedAt = positionOf(first))

    await {
      assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.isArrowFrom(shower, first) })
      assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.isArrowFrom(shower, second) })
    }
    assertEquals(arrows - 1, arrowsInTheBag())
  }

  @Test
  @Order(5)
  fun `Charge Arrow shoots once after drawing and takes one arrow`() {
    val blob = blobAt(Vec3L(-1, 0, 0))
    val charge = skillId("M_CHARGE_ARROW").toInt()
    val arrows = arrowsInTheBag()
    client.clearMessages()

    cast("M_CHARGE_ARROW", target = blob)

    await { assertEquals(1, client.countReceived(DamageEntitySMSG::class) { it.isArrowFrom(charge, blob) }) }
    assertEquals(arrows - 1, arrowsInTheBag())
  }

  @Test
  @Order(6)
  fun `Improve Concentration sharpens the Hunter and finds the hidden within 3 m only`() {
    val near = blobAt(Vec3L(0, 2, 0))
    val far = blobAt(Vec3L(0, 5, 0))
    listOf(near, far).forEach { id ->
      world.modify(id) { statusEffects.applyEffect(this, id, StatusEffectId.HIDDEN, 1, durationSeconds = HOLD_SECONDS) }
    }
    await { assertTrue(isHidden(near) && isHidden(far)) }
    val dexterity = combat().attributes.dexterity

    cast("M_IMPROVE_CONCENTRATION", target = hunter())

    await { assertFalse(isHidden(near)) }
    assertTrue(isHidden(far))
    await { assertEquals(dexterity + 10, combat().attributes.dexterity, "Improve Concentration 5") }
    assertTrue(world.read { getOrThrow(hunter(), StatusEffects::class).hasEffect(StatusEffectId.IMPROVE_CONCENTRATION.id) })
  }

  @Test
  @Order(7)
  fun `with an empty quiver, then without a bow, a bow skill is refused at the button`() {
    val target = blobs.first()
    client.sendMessage(SetQuiverCMSG(accountId, emptyList()))
    await { assertTrue(world.read { getOrThrow(hunter(), Equipment::class).getQuiver().isEmpty() }) }
    awaitReady(skillId("M_DOUBLE_STRAFE"))
    client.clearMessages()

    cast("M_DOUBLE_STRAFE", target = target)

    assertEquals(OpError.ATTACK_OUT_OF_AMMO, awaitRefusal())

    client.sendMessage(UnequipItemCMSG(accountId, EquipmentSlot.RIGHT_HAND))
    await { assertNull(combat().heldWeaponType) }
    client.clearMessages()

    cast("M_DOUBLE_STRAFE", target = target)

    assertEquals(OpError.SKILL_NEEDS_BOW, awaitRefusal())
  }

  /** A wild blob next to the Hunter that neither fights back nor wanders, and lasts the whole class. */
  private fun blobAt(offset: Vec3L): EntityId {
    val at = positionOf(hunter()) + offset
    val blob = world.read { spawner.spawnMob(this, BLOB, at, persistent = false) }
    blobs += blob
    world.modify(blob) { id ->
      remove(id, AiAgent::class)
      get(id, Health::class)!!.apply { max = BLOB_HEALTH; current = BLOB_HEALTH }
      // A concealment that cannot pay its upkeep ends by itself.
      get(id, Mana::class)?.apply { max = BLOB_MANA; current = BLOB_MANA }
    }
    // A shot is shown to whoever sees the target, which a new entity only has once a tick placed it in its chunk.
    await { assertTrue(accountId in visibility.observersOf(blob)) }

    return blob
  }

  private fun DamageEntitySMSG.isArrowFrom(skillId: Int, target: EntityId): Boolean {
    return entityId == target && attackId == skillId && projectileItemId == ARROW_ID
  }

  private fun cast(identifier: String, target: EntityId = 0L, aimedAt: Vec3L = positionOf(hunter())) {
    val skillId = skillId(identifier)
    world.modify(hunter()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, SKILLS.toMap().getValue(identifier), aimedAt, target))
  }

  private fun awaitRefusal(): OpError {
    var refusal: OperationErrorSMSG? = null
    await { refusal = assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }

    return refusal!!.code
  }

  private fun awaitReady(skillId: Long) {
    await { assertTrue(world.read { cooldowns.isReady(this, hunter(), skillId) }) }
  }

  private fun equip(identifier: String, itemId: Long) {
    val item = world.read {
      getOrThrow(hunter(), Inventory::class).getItems().first { !it.equipped && it.itemId == itemId }
    }
    client.clearMessages()
    client.sendMessage(EquipItemCMSG(accountId, item.playerItemId, EquipmentSlot.RIGHT_HAND))
    await {
      assertEquals(
        item.itemId,
        world.read { get(hunter(), Equipment::class)?.get(EquipmentSlot.RIGHT_HAND)?.itemId },
        "$identifier refused: ${client.tryGetLastReceived(OperationErrorSMSG::class)?.code}"
      )
    }
  }

  private fun arrowsInTheBag(): Int {
    return world.read {
      getOrThrow(hunter(), Inventory::class).getItems().filter { it.itemId == ARROW_ID && it.isStackable }.sumOf { it.amount }
    }
  }

  private fun isHidden(entityId: EntityId): Boolean {
    return world.read { getOrThrow(entityId, StatusEffects::class).hasEffect(StatusEffectId.HIDDEN.id) }
  }

  private fun combat(): CombatValues {
    return world.read { getOrThrow(hunter(), CombatStats::class).values }
  }

  private fun positionOf(entityId: EntityId): Vec3L {
    return world.read { getOrThrow(entityId, Position::class).toVec3L() }
  }

  private fun skillId(identifier: String): Long {
    return assertNotNull(skills.findByIdentifier(identifier)).id
  }

  private fun hunter(): EntityId {
    return connectionInfoService.getSelectedMasterEntityId(accountId)
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 43L
    const val BLOB = 1L
    const val HUNTER_LEVEL = 50
    const val BLOB_HEALTH = 100_000
    const val BLOB_MANA = 100_000
    const val MANA = 500

    /** Longer than the whole class runs. */
    const val HOLD_SECONDS = 600.0

    const val HUNTING_BOW = "hunting_bow"
    const val HUNTING_BOW_ID = 340L
    const val BOW_RANGE = 12L
    const val ARROW_ID = 96L

    val SKILLS = listOf(
      "M_OWLS_EYE" to 5, "M_VULTURES_EYE" to 5, "M_DOUBLE_STRAFE" to 10, "M_ARROW_SHOWER" to 5, "M_CHARGE_ARROW" to 1,
      "M_IMPROVE_CONCENTRATION" to 5,
    )
  }
}
