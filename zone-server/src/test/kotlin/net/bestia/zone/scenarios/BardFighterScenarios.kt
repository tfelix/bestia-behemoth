package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.aoi.EntityVisibility
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

/** A Bard learns the fighter half of its tree and casts each active over the real path, with whip and lute. */
class BardFighterScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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
  private lateinit var cooldowns: SkillCooldownService

  @Autowired
  private lateinit var visibility: EntityVisibility

  private lateinit var client: GameClientMock

  /** What the whip alone gives, before the passives are learned. */
  private lateinit var unskilled: CombatValues

  private val blobs = mutableListOf<EntityId>()

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createBard() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    val masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "lutefingers",
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
    listOf(LEATHER_WHIP, WOODEN_LUTE).forEach { inventoryService.addItem(masterId, it, 1) }

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
  fun `the Bard takes up a whip`() {
    // Whips are a Bard's; the profession is set here as the skills are below.
    world.modify(bard()) { id ->
      add(id, Professions(listOf(Profession.BARD)))
      repeat(BARD_LEVEL - 1) { get(id, Level::class)!!.inc() }
    }

    equip(LEATHER_WHIP, LEATHER_WHIP_ID)

    await { assertEquals(WeaponType.WHIP, combat().heldWeaponType) }
    unskilled = combat()
  }

  @Test
  @Order(2)
  fun `the Bard learns the fighter skills, and Instrument Mastery and Nimble Steps count`() {
    // Looked up first: SQL is refused inside the lease.
    val levels = SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(bard()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      add(id, IsStatusValueDirty)
    }

    await {
      assertEquals(unskilled.weapon.atk + 2 * 10, combat().weapon.atk, "Instrument Mastery 10 with a whip")
      assertEquals(unskilled.derived.flee + 2 * 5, combat().derived.flee, "Nimble Steps 5 in the novice shirt")
    }
  }

  @Test
  @Order(3)
  fun `Hobbling Lash strikes a foe and slows it`() {
    val blob = blobAt(Vec3L(1, 0, 0))
    val lash = skillId("M_HOBBLING_LASH").toInt()
    client.clearMessages()

    cast("M_HOBBLING_LASH", target = blob)

    await { assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == lash }) }
    await { assertTrue(has(blob, StatusEffectId.SLOWED)) }
  }

  @Test
  @Order(4)
  fun `with a whip in hand Discord Strike is refused`() {
    client.clearMessages()

    cast("M_DISCORD_STRIKE", target = blobs.first())

    assertEquals(OpError.SKILL_NEEDS_INSTRUMENT, awaitRefusal())
  }

  @Test
  @Order(5)
  fun `the Bard takes up a lute, and Instrument Mastery counts for it too`() {
    // A lute needs both hands, and the starter kit fills the left one.
    client.sendMessage(UnequipItemCMSG(accountId, EquipmentSlot.LEFT_HAND))
    await { assertNull(world.read { get(bard(), Equipment::class)?.get(EquipmentSlot.LEFT_HAND) }) }

    equip(WOODEN_LUTE, WOODEN_LUTE_ID)

    await { assertEquals(WeaponType.INSTRUMENT, combat().heldWeaponType) }
    val gearBesidesTheWhip = unskilled.weapon.atk - LEATHER_WHIP_ATK
    await { assertEquals(gearBesidesTheWhip + WOODEN_LUTE_ATK + 2 * 10, combat().weapon.atk) }
  }

  @Test
  @Order(6)
  fun `Discord Strike hits a foe with a sound wave`() {
    val blob = blobs.first()
    val discord = skillId("M_DISCORD_STRIKE").toInt()
    client.clearMessages()

    cast("M_DISCORD_STRIKE", target = blob)

    await { assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == discord }) }
  }

  @Test
  @Order(7)
  fun `Dissonant Chord rattles the foes within 3 tiles only`() {
    val near = blobs.first()
    val far = blobAt(Vec3L(0, 5, 0))

    cast("M_DISSONANT_CHORD", target = bard())

    await { assertTrue(has(near, StatusEffectId.DISSONANCE)) }
    assertFalse(has(far, StatusEffectId.DISSONANCE))
    assertFalse(has(bard(), StatusEffectId.DISSONANCE), "never the Bard's own side")
  }

  @Test
  @Order(8)
  fun `with a lute in hand Hobbling Lash is refused`() {
    awaitReady(skillId("M_HOBBLING_LASH"))
    client.clearMessages()

    cast("M_HOBBLING_LASH", target = blobs.first())

    assertEquals(OpError.SKILL_NEEDS_WHIP, awaitRefusal())
  }

  /** A wild blob next to the Bard that neither fights back nor wanders, and lasts the whole class. */
  private fun blobAt(offset: Vec3L): EntityId {
    val at = positionOf(bard()) + offset
    val blob = world.read { spawner.spawnMob(this, BLOB, at, persistent = false) }
    blobs += blob
    world.modify(blob) { id ->
      remove(id, AiAgent::class)
      get(id, Health::class)!!.apply { max = BLOB_HEALTH; current = BLOB_HEALTH }
    }
    // A blow is shown to whoever sees the target, which a new entity only has once a tick placed it in its chunk.
    await { assertTrue(accountId in visibility.observersOf(blob)) }

    return blob
  }

  private fun cast(identifier: String, target: EntityId) {
    val skillId = skillId(identifier)
    world.modify(bard()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, SKILLS.toMap().getValue(identifier), positionOf(bard()), target))
  }

  private fun awaitRefusal(): OpError {
    var refusal: OperationErrorSMSG? = null
    await { refusal = assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }

    return refusal!!.code
  }

  private fun awaitReady(skillId: Long) {
    await { assertTrue(world.read { cooldowns.isReady(this, bard(), skillId) }) }
  }

  private fun equip(identifier: String, itemId: Long) {
    val item = world.read {
      getOrThrow(bard(), Inventory::class).getItems().first { !it.equipped && it.itemId == itemId }
    }
    client.clearMessages()
    client.sendMessage(EquipItemCMSG(accountId, item.playerItemId, EquipmentSlot.RIGHT_HAND))
    await {
      assertEquals(
        item.itemId,
        world.read { get(bard(), Equipment::class)?.get(EquipmentSlot.RIGHT_HAND)?.itemId },
        "$identifier refused: ${client.tryGetLastReceived(OperationErrorSMSG::class)?.code}"
      )
    }
  }

  private fun has(entityId: EntityId, effect: StatusEffectId): Boolean {
    return world.read { get(entityId, StatusEffects::class)?.hasEffect(effect.id) == true }
  }

  private fun combat(): CombatValues {
    return world.read { getOrThrow(bard(), CombatStats::class).values }
  }

  private fun positionOf(entityId: EntityId): Vec3L {
    return world.read { getOrThrow(entityId, Position::class).toVec3L() }
  }

  private fun skillId(identifier: String): Long {
    return assertNotNull(skills.findByIdentifier(identifier)).id
  }

  private fun bard(): EntityId {
    return connectionInfoService.getSelectedMasterEntityId(accountId)
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 44L
    const val BLOB = 1L
    const val BARD_LEVEL = 50
    const val BLOB_HEALTH = 100_000
    const val MANA = 500

    const val LEATHER_WHIP = "leather_whip"
    const val LEATHER_WHIP_ID = 312L
    const val LEATHER_WHIP_ATK = 15
    const val WOODEN_LUTE = "wooden_lute"
    const val WOODEN_LUTE_ID = 313L
    const val WOODEN_LUTE_ATK = 8

    val SKILLS = listOf(
      "M_INSTRUMENT_MASTERY" to 10, "M_NIMBLE_STEPS" to 5, "M_HOBBLING_LASH" to 5, "M_DISCORD_STRIKE" to 5,
      "M_DISSONANT_CHORD" to 5,
    )
  }
}
