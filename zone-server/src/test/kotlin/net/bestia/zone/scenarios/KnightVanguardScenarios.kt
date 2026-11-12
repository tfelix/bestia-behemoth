package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ai.core.state.CommonKeys
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.Taunted
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
import net.bestia.zone.item.profession.Profession
import net.bestia.zone.item.profession.Professions
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.navigation.local.LocalWalkQuery
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A Knight in plate learns the vanguard half of its tree and casts each active over the real path. */
class KnightVanguardScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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
  private lateinit var walk: LocalWalkQuery

  @Autowired
  private lateinit var cooldowns: SkillCooldownService

  private lateinit var client: GameClientMock

  private var dummy: EntityId? = null
  private var brute: EntityId? = null

  /** What the Knight's gear alone gives, before the masteries are learned. */
  private lateinit var unskilled: CombatValues

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createKnight() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    val masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "ironwall",
        hairColor = Color.WHITE,
        skinColor = Color.WHITE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id
    listOf(IRON_SPEAR, IRON_CUIRASS, TOWER_SHIELD, IRON_AXE).forEach { inventoryService.addItem(masterId, it, 1) }

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
    // Charge dashes only over ground the server holds walk data for, as it does around a real player.
    holdTerrain(client)
  }

  /** Every Spring context shares one world and one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    client.disconnect()
    listOfNotNull(dummy, brute).forEach { id -> world.modify(id) { if (isAlive(id)) destroy(id) } }
  }

  @Test
  @Order(1)
  fun `the Knight takes up a spear, plate and a tower shield`() {
    // Heavy armor is a Knight's; the profession is set here as the skills are below.
    world.modify(knight()) { id ->
      add(id, Professions(listOf(Profession.KNIGHT)))
      repeat(KNIGHT_LEVEL - 1) { get(id, Level::class)!!.inc() }
      get(id, Health::class)!!.apply { max = KNIGHT_HEALTH; current = KNIGHT_HEALTH }
    }

    equip(IRON_SPEAR, EquipmentSlot.RIGHT_HAND)
    equip(IRON_CUIRASS, EquipmentSlot.ARMOR)
    equip(TOWER_SHIELD, EquipmentSlot.LEFT_HAND)

    unskilled = awaitValue {
      combat().also { assertTrue(it.defense.hardDefense > 0 && it.weapon.atk > 0, "the gear has not counted yet") }
    }
  }

  @Test
  @Order(2)
  fun `the Knight learns the vanguard skills, and the masteries make the gear count for more`() {
    // Looked up first: SQL is refused inside the lease.
    val levels = SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(knight()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      add(id, IsStatusValueDirty)
    }

    await {
      val skilled = combat()
      assertEquals(unskilled.weapon.atk + 3 * 10, skilled.weapon.atk, "Heavy Weapon Mastery 10 with a spear")
      assertEquals(unskilled.defense.hardDefense + 2 * 10 * 2, skilled.defense.hardDefense, "Heavy Armor Mastery 10, two heavy pieces")
    }

    val at = positionOf(knight())
    // Loads the learnset now: the spawner reads it inside the lease, where SQL is refused.
    catalogue.learnset(BLOB)
    val blob = world.read { spawner.spawnMob(this, BLOB, at + Vec3L(1, 0, 0), persistent = false) }
    dummy = blob
    // A blob that strikes back or wanders off breaks the casts that follow.
    world.modify(blob) { id ->
      remove(id, AiAgent::class)
      get(id, Health::class)!!.apply { max = BLOB_HEALTH; current = BLOB_HEALTH }
    }
  }

  @Test
  @Order(3)
  fun `Provoke turns a wild bestia on the Knight`() {
    val at = positionOf(knight())
    // Keeps its AI: the point is whom it then picks.
    val provoked = world.read { spawner.spawnMob(this, BLOB, at + Vec3L(0, 2, 0), persistent = false) }
    brute = provoked
    world.modify(provoked) { id -> get(id, Health::class)!!.apply { max = BLOB_HEALTH; current = BLOB_HEALTH } }

    cast("M_PROVOKE", target = provoked)

    await { assertEquals(Taunted(knight()), world.read { get(provoked, Taunted::class) }) }
    await { assertEquals(knight(), world.read { get(provoked, AiAgent::class)?.memory?.get(CommonKeys.TARGET_ID) }) }

    // It would go on fighting the Knight through the casts that follow.
    world.modify(provoked) { id -> destroy(id) }
    brute = null
  }

  @Test
  @Order(4)
  fun `Bash strikes the wild bestia`() {
    val blob = assertNotNull(dummy)
    val bash = skillId("M_BASH").toInt()
    client.clearMessages()

    cast("M_BASH", target = blob)

    await { assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == bash }) }
  }

  @Test
  @Order(5)
  fun `Brandish Spear sweeps the bestia in front of the Knight`() {
    val blob = assertNotNull(dummy)
    val brandish = skillId("M_BRANDISH_SPEAR").toInt()
    client.clearMessages()

    cast("M_BRANDISH_SPEAR", target = blob)

    await { assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == brandish }) }
  }

  @Test
  @Order(6)
  fun `Charge dashes the Knight up to the bestia, strikes it and provokes it`() {
    val blob = assertNotNull(dummy)
    val charge = skillId("M_CHARGE").toInt()
    val runUp = runUpTo(positionOf(blob))
    world.modify(knight()) { id -> get(id, Position::class)!!.apply { x = runUp.x; y = runUp.y; z = runUp.z } }
    client.clearMessages()

    cast("M_CHARGE", target = blob)

    await { assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == charge }) }
    assertEquals(1, chebyshev(positionOf(knight()), positionOf(blob)), "the Knight stands next to the bestia")
    assertTrue(has(blob, StatusEffectId.PROVOKED))
  }

  @Test
  @Order(7)
  fun `with an axe in hand Brandish Spear is refused`() {
    equip(IRON_AXE, EquipmentSlot.RIGHT_HAND)
    val brandish = skillId("M_BRANDISH_SPEAR")
    await { assertTrue(world.read { cooldowns.isReady(this, knight(), brandish) }) }
    client.clearMessages()

    cast("M_BRANDISH_SPEAR", target = assertNotNull(dummy))

    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.SKILL_NEEDS_SPEAR, refusal.code)
  }

  private fun cast(identifier: String, target: EntityId) {
    val skillId = skillId(identifier)
    world.modify(knight()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, SKILLS.toMap().getValue(identifier), positionOf(knight()), target))
  }

  private fun equip(identifier: String, slot: EquipmentSlot) {
    val item = world.read {
      getOrThrow(knight(), Inventory::class).getItems().first { !it.equipped && it.itemId == itemIdOf(identifier) }
    }
    client.sendMessage(EquipItemCMSG(accountId, item.playerItemId, slot))
    await { assertEquals(item.itemId, worn(slot)?.itemId) }
  }

  private fun itemIdOf(identifier: String): Long {
    return when (identifier) {
      IRON_SPEAR -> 307L
      IRON_AXE -> IRON_AXE_ID
      IRON_CUIRASS -> 20L
      TOWER_SHIELD -> 320L
      else -> error("not in the Knight's bag: $identifier")
    }
  }

  /**
   * A tile [RUN_UP_TILES] away from [target] from which a dash reaches it: every step on the way is ground the walk
   * data lets a body step onto, as the dash will ask.
   */
  private fun runUpTo(target: Vec3L): Vec3L {
    val directions = listOf(-1 to 0, 0 to -1, 0 to 1, 1 to 0, -1 to -1, -1 to 1, 1 to -1, 1 to 1)

    return awaitValue {
      val found = directions.firstNotNullOfOrNull { (dx, dy) -> walkableLine(target, dx, dy) }
      assertNotNull(found, "no run-up to the bestia the ground allows")
    }
  }

  /** The start of a walkable straight line from [RUN_UP_TILES] out in direction ([dx], [dy]) to next to [target]. */
  private fun walkableLine(target: Vec3L, dx: Int, dy: Int): Vec3L? {
    val tiles = (RUN_UP_TILES downTo 1).map { distance ->
      val column = Vec3L(target.x + dx * distance, target.y + dy * distance, target.z)
      column.copy(z = world.read { walk.surfaceAt(column) } ?: return null)
    }
    val walkable = tiles.zipWithNext().all { (from, to) -> world.read { walk.canStep(from, to) } }

    return tiles.first().takeIf { walkable }
  }

  private fun chebyshev(a: Vec3L, b: Vec3L): Long {
    return max(abs(a.x - b.x), abs(a.y - b.y))
  }

  private fun combat(): CombatValues {
    return world.read { getOrThrow(knight(), CombatStats::class).values }
  }

  private fun worn(slot: EquipmentSlot): Equipment.EquippedItem? {
    return world.read { get(knight(), Equipment::class)?.get(slot) }
  }

  private fun positionOf(entityId: EntityId): Vec3L {
    return world.read { getOrThrow(entityId, Position::class).toVec3L() }
  }

  private fun has(entityId: EntityId, effect: StatusEffectId): Boolean {
    return world.read { get(entityId, StatusEffects::class)?.hasEffect(effect.id) == true }
  }

  private fun skillId(identifier: String): Long {
    return assertNotNull(skills.findByIdentifier(identifier)).id
  }

  private fun knight(): EntityId {
    return connectionInfoService.getSelectedMasterEntityId(accountId)
  }

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 38L
    const val BLOB = 1L
    const val KNIGHT_LEVEL = 50
    const val KNIGHT_HEALTH = 100_000
    const val BLOB_HEALTH = 100_000
    const val MANA = 500
    const val RUN_UP_TILES = 4
    const val IRON_SPEAR = "iron_spear"
    const val IRON_AXE = "iron_axe"
    const val IRON_AXE_ID = 304L
    const val IRON_CUIRASS = "iron_cuirass"
    const val TOWER_SHIELD = "tower_shield"

    val SKILLS = listOf(
      "M_HEAVY_WEAPON_MASTERY" to 10, "M_HEAVY_ARMOR_MASTERY" to 10, "M_PROVOKE" to 5, "M_BASH" to 5,
      "M_BRANDISH_SPEAR" to 5, "M_CHARGE" to 5,
    )
  }
}
