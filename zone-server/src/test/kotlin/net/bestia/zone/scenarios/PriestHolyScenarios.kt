package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.casting.scripts.master.AquaBenedicta
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.Dead
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
import net.bestia.zone.world.ClientWorldContract
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
import kotlin.test.assertTrue

/** A Priest casts each of its holy rites over the real message path and the tick. */
class PriestHolyScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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
  private lateinit var playerBestiaCreateOperation: PlayerBestiaCreateOperation

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var skills: SkillRepository

  @Autowired
  private lateinit var spawner: BestiaEntitySpawner

  @Autowired
  private lateinit var catalogue: BestiaCatalogue

  private lateinit var client: GameClientMock

  private var masterId = 0L

  private var foe: EntityId? = null

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createPriest() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "exorcist",
        hairColor = Color.WHITE,
        skinColor = Color.WHITE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id
    playerBestiaCreateOperation.createAndSpawn(masterId, PlayerBestiaCreateData("blob", Vec3L.ZERO))
    // Before connecting: the live bag is read from the database when the master enters the world.
    inventoryService.grantToMaster(masterId, EMPTY_BOTTLE_ITEM_ID, 1)
    inventoryService.grantToMaster(masterId, AquaBenedicta.HOLY_WATER_ITEM_ID, 1)

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
  }

  /** Every Spring context shares one world and one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    val priest = priest()
    val areas = areasWhere { it.casterId == priest }
    client.disconnect()
    (areas + listOfNotNull(foe) + ownBestias()).forEach { id -> world.modify(id) { destroy(it) } }
  }

  @Test
  @Order(1)
  fun `the Priest learns every holy rite, and Demon Bane weighs the unholy`() {
    // Looked up first: SQL is refused inside the lease.
    val levels = HOLY_SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(priest()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      repeat(PRIEST_LEVEL - 1) { get(id, Level::class)!!.inc() }
      add(id, IsStatusValueDirty)
    }

    await {
      val modifiers = world.read { get(priest(), CombatStats::class)!!.values.damageModifiers }
      assertEquals(25, modifiers.dealtVsUnholy)
      assertEquals(30, modifiers.takenFromUnholy)
    }
  }

  @Test
  @Order(2)
  fun `Holy Light and Turn Undead strike a wild bestia`() {
    // Loads the learnset now: the spawner reads it inside the lease, where SQL is refused.
    catalogue.learnset(BLOB)

    for (identifier in listOf("M_HOLY_LIGHT", "M_TURN_UNDEAD")) {
      val skillId = skillId(identifier)
      // A fresh blob beside the Priest for each cast, without its AI and in one lease, so it never acts: a blob
      // that strikes back cancels the cast with its hit, and its skill lookup would run SQL on the tick.
      val at = world.read { getOrThrow(priest(), Position::class).toVec3L() }
      // In the Priest's own chunk: its client is told of an entity only once that chunk streams to it, and a
      // neighbouring chunk may not have arrived yet, so the client would never hear the blow land.
      val beside = if (Math.floorMod(at.x + 1, ClientWorldContract.CHUNK_SIZE.toLong()) == 0L) -1 else 1
      val blob = world.read {
        val id = spawner.spawnMob(this, BLOB, at + Vec3L(beside, 0, 0), persistent = false)
        remove(id, AiAgent::class)
        // Enough to survive the blow: a dead blob would fizzle the cast.
        get(id, Health::class)!!.apply { max = BLOB_HEALTH; current = BLOB_HEALTH }
        id
      }
      foe = blob
      client.clearMessages()

      cast(identifier, level = 1, target = blob)

      await {
        assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == skillId.toInt() })
      }

      // Gone before the Priest lies at 1 HP below, where a blob that strikes back could kill it.
      world.modify(blob) { destroy(it) }
      foe = null
    }
  }

  @Test
  @Order(3)
  fun `Magnus Exorcismus and Sanctuary lay their holy ground`() {
    world.modify(priest()) { id -> get(id, Health::class)!!.current = 1 }

    cast("M_MAGNUS_EXORCISMUS", level = 3)
    await { assertTrue(areasOf("M_MAGNUS_EXORCISMUS").isNotEmpty()) }

    cast("M_SANCTUARY", level = 3)
    await { assertTrue(areasOf("M_SANCTUARY").isNotEmpty()) }
    // 15% of max HP a second; regeneration alone is nowhere near that fast.
    await {
      val health = world.read { get(priest(), Health::class)!! }
      assertTrue(health.current >= health.max * 15 / 100, "Sanctuary healed to ${health.current} of ${health.max}")
    }
  }

  @Test
  @Order(4)
  fun `Pneuma shelters whoever stands in it`() {
    cast("M_PNEUMA")

    await { assertTrue(has(priest(), StatusEffectId.SHELTERED)) }
  }

  @Test
  @Order(5)
  fun `Aspersio spends a Holy Water and makes the weapon holy`() {
    cast("M_ASPERSIO", level = 3)

    await { assertTrue(has(priest(), StatusEffectId.ASPERSIO)) }
    await { assertEquals(0, carried(AquaBenedicta.HOLY_WATER_ITEM_ID)) }
  }

  @Test
  @Order(6)
  fun `Aqua Benedicta refuses a Priest on dry land before the cast bar`() {
    client.clearMessages()

    cast("M_AQUA_BENEDICTA")

    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.SKILL_NEEDS_WATER, refusal.code)
    assertEquals(1, carried(EMPTY_BOTTLE_ITEM_ID))
  }

  @Test
  @Order(7)
  fun `Resurrection raises the Priest's fallen bestia with 30 percent of its HP`() {
    val bestia = ownBestias().single()
    val at = world.read { getOrThrow(priest(), Position::class).toVec3L() }
    world.modify(bestia) { id ->
      get(id, Position::class)!!.apply { x = at.x + 1; y = at.y; z = at.z }
      get(id, Health::class)!!.current = 0
      add(id, Dead())
    }

    cast("M_RESURRECTION", target = bestia)

    await { assertFalse(world.read { has(bestia, Dead::class) }) }
    val health = world.read { get(bestia, Health::class)!! }
    // Regeneration ticks may have landed since, several of them on a loaded machine: at 17 max HP the old
    // 30-40% window held a single value, and one tick put the bestia out of it.
    assertTrue(health.current in health.max * 30 / 100 until health.max / 2, "raised with ${health.current} of ${health.max}")
  }

  private fun cast(identifier: String, level: Int = 1, target: EntityId = 0L) {
    val skillId = skillId(identifier)
    world.modify(priest()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    val at = world.read { getOrThrow(priest(), Position::class).toVec3L() }
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, level, at, target))
  }

  private fun areasOf(identifier: String): List<EntityId> {
    val skillId = skillId(identifier)

    return areasWhere { it.skillId == skillId }
  }

  private fun areasWhere(predicate: (AreaEffect) -> Boolean): List<EntityId> {
    val found = mutableListOf<EntityId>()
    world.read { query(AreaEffect::class).each { id -> if (predicate(get<AreaEffect>())) found.add(id) } }

    return found
  }

  private fun carried(itemId: Long): Int {
    return world.read { get(priest(), Inventory::class)!!.getItems().filter { it.itemId == itemId }.sumOf { it.amount } }
  }

  private fun has(entityId: EntityId, effect: StatusEffectId): Boolean {
    return world.read { get(entityId, StatusEffects::class)?.hasEffect(effect.id) == true }
  }

  private fun ownBestias(): List<EntityId> {
    return world.read { OwnedBestia.ownedBy(this, masterId) }.map { it.entityId }
  }

  private fun skillId(identifier: String): Long = assertNotNull(skills.findByIdentifier(identifier)).id

  private fun priest(): EntityId = connectionInfoService.getSelectedMasterEntityId(accountId)

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 33L
    const val BLOB = 1L
    const val PRIEST_LEVEL = 50
    const val BLOB_HEALTH = 100_000

    /** Resurrection's 120 and then some: a cast sets the pool rather than waiting for a level that pays it. */
    const val MANA = 500

    /** `empty_bottle` in `items.yml`, Aqua Benedicta's reagent. */
    const val EMPTY_BOTTLE_ITEM_ID = 109L

    val HOLY_SKILLS = listOf(
      "M_HOLY_LIGHT" to 5, "M_DEMON_BANE" to 5, "M_TURN_UNDEAD" to 3, "M_MAGNUS_EXORCISMUS" to 3,
      "M_SANCTUARY" to 3, "M_PNEUMA" to 1, "M_AQUA_BENEDICTA" to 1, "M_ASPERSIO" to 3, "M_RESURRECTION" to 1,
    )
  }
}
