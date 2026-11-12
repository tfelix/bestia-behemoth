package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.RegenerationModifiers
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.BestiaCatalogue
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
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A Wizard learns the Warrior trunk and the arcane half of its tree, and casts each active over the real path. */
class WizardArcaneScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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

  @Autowired
  private lateinit var cooldowns: SkillCooldownService

  private lateinit var client: GameClientMock

  private var masterId = 0L

  private var foe: EntityId? = null

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createWizard() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "arcanist",
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
    inventoryService.grantToMaster(masterId, RED_MANA_CRYSTAL_ITEM_ID, 1)

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
  }

  /** Every Spring context shares one world and one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    val wizard = wizard()
    val areas = areasWhere { it.casterId == wizard }
    client.disconnect()
    (areas + listOfNotNull(foe) + ownBestias()).forEach { id -> world.modify(id) { destroy(it) } }
  }

  @Test
  @Order(1)
  fun `the Wizard learns the trunk and the arcane skills, and the passives take hold`() {
    // Looked up first: SQL is refused inside the lease.
    val levels = SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(wizard()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      repeat(WIZARD_LEVEL - 1) { get(id, Level::class)!!.inc() }
      add(id, IsStatusValueDirty)
    }

    await {
      val values = world.read { get(wizard(), CombatStats::class)!!.values }
      assertEquals(15, values.damageModifiers.takenByForm[HitForm.PHYSICAL])
      assertEquals(25, values.arcaneDamagePercent)
      assertTrue(world.read { get(wizard(), RegenerationModifiers::class)!!.hp.inCombat })
    }
  }

  @Test
  @Order(2)
  fun `Magic Armor wards the Wizard`() {
    cast("M_MAGIC_ARMOR", level = 3)

    await { assertTrue(has(wizard(), StatusEffectId.MAGIC_ARMOR)) }
    await { assertEquals(30, world.read { get(wizard(), CombatStats::class)!!.values.damageModifiers.takenFromAnyHit }) }
  }

  @Test
  @Order(3)
  fun `each bolt and Soul Break strike a wild bestia, and Soul Break tears at its mana`() {
    val at = world.read { getOrThrow(wizard(), Position::class).toVec3L() }
    // Loads the learnset now: the spawner reads it inside the lease, where SQL is refused.
    catalogue.learnset(BLOB)
    val blob = world.read { spawner.spawnMob(this, BLOB, at + Vec3L(3, 0, 0), persistent = false) }
    foe = blob
    // A blob that strikes back breaks every cast bar after the first hit.
    world.modify(blob) { id -> remove(id, AiAgent::class) }

    for (identifier in listOf("M_FIREBOLT", "M_ICE_BOLT", "M_THUNDER_BOLT", "M_EARTH_SPIKE", "M_SOUL_BREAK")) {
      val skillId = skillId(identifier)
      // Enough to survive the blow: a dead blob would fizzle the next cast.
      world.modify(blob) { id ->
        get(id, Health::class)!!.apply { max = BLOB_HEALTH; current = BLOB_HEALTH }
        (get(id, Mana::class) ?: add(id, Mana(BLOB_MANA, BLOB_MANA))).apply { max = BLOB_MANA; current = BLOB_MANA }
      }
      client.clearMessages()

      cast(identifier, level = levelOf(identifier), target = blob)

      await {
        assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == skillId.toInt() })
      }
    }

    // 25% of the blob's mana went at Lv.3; a regeneration tick may have landed since.
    assertTrue(world.read { get(blob, Mana::class)!!.current } <= BLOB_MANA * 80 / 100)
  }

  @Test
  @Order(4)
  fun `Stone Curse burns its crystal and Suppress Magic is spent on the wild bestia`() {
    val blob = assertNotNull(foe)

    cast("M_STONE_CURSE", level = 3, target = blob)
    await { assertEquals(0, carried(RED_MANA_CRYSTAL_ITEM_ID)) }

    // A roll, like the curse: that it resolved is what the cooldown says.
    val suppressMagic = skillId("M_SUPPRESS_MAGIC")
    cast("M_SUPPRESS_MAGIC", level = 3, target = blob)
    await { assertFalse(world.read { cooldowns.isReady(this, wizard(), suppressMagic) }) }

    world.modify(blob) { destroy(it) }
    foe = null
  }

  @Test
  @Order(5)
  fun `Safety Wall shields the Wizard standing on it`() {
    cast("M_SAFETY_WALL", level = 3)

    await { assertTrue(areasOf("M_SAFETY_WALL").isNotEmpty()) }
    await { assertTrue(has(wizard(), StatusEffectId.SAFETY_WALL)) }
  }

  @Test
  @Order(6)
  fun `Mindbreak breaks the Wizard's own bestia and refuses anything else`() {
    val bestia = ownBestias().single()
    val at = world.read { getOrThrow(wizard(), Position::class).toVec3L() }
    // Kept still: a bestia that wanders off during the 3s bar is out of reach when the cast resolves.
    world.modify(bestia) { id ->
      remove(id, AiAgent::class)
      get(id, Position::class)!!.apply { x = at.x + 1; y = at.y; z = at.z }
    }

    client.clearMessages()
    cast("M_MINDBREAK", level = 3, target = wizard())
    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.SKILL_NEEDS_OWN_BESTIA, refusal.code)

    cast("M_MINDBREAK", level = 3, target = bestia)
    await { assertTrue(has(bestia, StatusEffectId.MINDBREAK)) }
  }

  private fun cast(identifier: String, level: Int = 1, target: EntityId = 0L) {
    val skillId = skillId(identifier)
    world.modify(wizard()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    val at = world.read { getOrThrow(wizard(), Position::class).toVec3L() }
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, level, at, target))
  }

  private fun levelOf(identifier: String): Int = SKILLS.toMap().getValue(identifier)

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
    return world.read { get(wizard(), Inventory::class)!!.getItems().filter { it.itemId == itemId }.sumOf { it.amount } }
  }

  private fun has(entityId: EntityId, effect: StatusEffectId): Boolean {
    return world.read { get(entityId, StatusEffects::class)?.hasEffect(effect.id) == true }
  }

  private fun ownBestias(): List<EntityId> {
    return world.read { OwnedBestia.ownedBy(this, masterId) }.map { it.entityId }
  }

  private fun skillId(identifier: String): Long = assertNotNull(skills.findByIdentifier(identifier)).id

  private fun wizard(): EntityId = connectionInfoService.getSelectedMasterEntityId(accountId)

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 34L
    const val BLOB = 1L
    const val WIZARD_LEVEL = 50
    const val BLOB_HEALTH = 100_000
    const val BLOB_MANA = 1_000
    const val MANA = 500

    /** `red_mana_crystal` in `items.yml`, Stone Curse's reagent. */
    const val RED_MANA_CRYSTAL_ITEM_ID = 339L

    val SKILLS = listOf(
      "M_ENDURE" to 1, "M_IRON_SKIN" to 5, "M_MAGIC_ARMOR" to 3,
      "M_FIREBOLT" to 5, "M_ICE_BOLT" to 5, "M_THUNDER_BOLT" to 5, "M_EARTH_SPIKE" to 5,
      "M_ARCANE_MASTERY" to 3, "M_SOUL_BREAK" to 3, "M_MINDBREAK" to 3,
      "M_SAFETY_WALL" to 3, "M_STONE_CURSE" to 3, "M_SUPPRESS_MAGIC" to 3,
    )
  }
}
