package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.SkillCooldownService
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.Dead
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A Brawler with an oak staff learns the endurance half of its tree and uses each skill over the real path. */
class BrawlerEnduranceScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createBrawler() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    val masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "stonejaw",
        hairColor = Color.WHITE,
        skinColor = Color.WHITE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id
    inventoryService.addItem(masterId, OAK_STAFF, 1)

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
    // Rush dashes only over ground the server holds walk data for, as it does around a real player.
    holdTerrain(client)
  }

  /** Every Spring context shares one world and one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    client.disconnect()
    listOfNotNull(dummy).forEach { id -> world.modify(id) { if (isAlive(id)) destroy(id) } }
  }

  @Test
  @Order(1)
  fun `the Brawler takes up a staff and learns the endurance skills, and the passives count`() {
    world.modify(brawler()) { id ->
      add(id, Professions(listOf(Profession.BRAWLER)))
      repeat(BRAWLER_LEVEL - 1) { get(id, Level::class)!!.inc() }
    }
    equipStaff()

    // Looked up first: SQL is refused inside the lease.
    val levels = SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(brawler()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      add(id, IsStatusValueDirty)
    }

    await {
      val values = combat()
      assertEquals(50, values.staminaCostReduction, "Tough Guy 5")
      assertEquals(360, values.lastStandCooldownSeconds, "Unbreakable 3")
      assertTrue(values.halvesStunAndKnockback, "Unbreakable 3")
    }

    val at = positionOf(brawler())
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
  @Order(2)
  fun `Second Wind gives back half the stamina and a fifth of the HP`() {
    val (maxStamina, maxHealth) = world.modify(brawler()) { id ->
      val stamina = get(id, Stamina::class)!!.apply { current = 0 }
      val health = get(id, Health::class)!!.apply { current = max / 4 }
      stamina.max to health.max
    }!!

    cast("M_SECOND_WIND")

    // Regeneration may add on top, never take away.
    await {
      val (stamina, health) = world.read { getOrThrow(brawler(), Stamina::class).current to getOrThrow(brawler(), Health::class).current }
      assertTrue(stamina >= maxStamina / 2, "stamina $stamina of $maxStamina")
      assertTrue(health >= maxHealth / 4 + maxHealth / 5, "HP $health of $maxHealth")
    }
  }

  @Test
  @Order(3)
  fun `Battle Fury goes Berserk, and no physical defence is left`() {
    cast("M_BATTLE_FURY")

    await { assertTrue(has(brawler(), StatusEffectId.BERSERK)) }
    await {
      val defense = combat().defense
      assertEquals(0, defense.defense + defense.defenseSpread + defense.hardDefense)
    }
  }

  @Test
  @Order(4)
  fun `Staff Sweep strikes the bestia next to the Brawler`() {
    val blob = assertNotNull(dummy)
    val sweep = skillId("M_STAFF_SWEEP").toInt()
    client.clearMessages()

    cast("M_STAFF_SWEEP")

    await { assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == sweep }) }
  }

  @Test
  @Order(5)
  fun `Rush dashes the Brawler up to the bestia and strikes it`() {
    val blob = assertNotNull(dummy)
    val rush = skillId("M_RUSH").toInt()
    val runUp = runUpTo(positionOf(blob))
    world.modify(brawler()) { id -> get(id, Position::class)!!.apply { x = runUp.x; y = runUp.y; z = runUp.z } }
    client.clearMessages()

    cast("M_RUSH", target = blob)

    await { assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == rush }) }
    assertEquals(1, chebyshev(positionOf(brawler()), positionOf(blob)), "the Brawler stands next to the bestia")
  }

  @Test
  @Order(6)
  fun `Unbreakable leaves the Brawler at 1 HP after a lethal blow, and starts its cooldown`() {
    val blob = assertNotNull(dummy)
    world.modify(brawler()) { id ->
      val health = get(id, Health::class)!!.apply { current = max }
      val staged = get(id, IncomingDamage::class) ?: add(id, IncomingDamage())
      staged.add(health.max * 3, blob)
    }

    await { assertTrue(has(brawler(), StatusEffectId.UNBREAKABLE_SPENT)) }
    val health = world.read { getOrThrow(brawler(), Health::class).current }
    // A regeneration tick may already have added to the 1 HP left.
    assertTrue(health >= 1, "HP $health")
    assertFalse(world.read { has(brawler(), Dead::class) })

    world.modify(brawler()) { id -> get(id, Health::class)!!.apply { current = max } }
  }

  @Test
  @Order(7)
  fun `without a staff Staff Sweep is refused at the button`() {
    client.sendMessage(UnequipItemCMSG(accountId, EquipmentSlot.RIGHT_HAND))
    await { assertEquals(null, worn(EquipmentSlot.RIGHT_HAND)) }
    val sweep = skillId("M_STAFF_SWEEP")
    await { assertTrue(world.read { cooldowns.isReady(this, brawler(), sweep) }) }
    client.clearMessages()

    cast("M_STAFF_SWEEP")

    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.SKILL_NEEDS_STAFF, refusal.code)
  }

  private fun cast(identifier: String, target: EntityId = 0L) {
    val skillId = skillId(identifier)
    world.modify(brawler()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, SKILLS.toMap().getValue(identifier), positionOf(brawler()), target))
  }

  private fun equipStaff() {
    // The staff takes both hands, and a new master holds the novice shield.
    client.sendMessage(UnequipItemCMSG(accountId, EquipmentSlot.LEFT_HAND))
    await { assertEquals(null, worn(EquipmentSlot.LEFT_HAND)) }
    val item = world.read {
      getOrThrow(brawler(), Inventory::class).getItems().first { !it.equipped && it.itemId == OAK_STAFF_ID }
    }
    client.sendMessage(EquipItemCMSG(accountId, item.playerItemId, EquipmentSlot.RIGHT_HAND))
    await { assertEquals(item.itemId, worn(EquipmentSlot.RIGHT_HAND)?.itemId) }
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
    return world.read { getOrThrow(brawler(), CombatStats::class).values }
  }

  private fun worn(slot: EquipmentSlot): Equipment.EquippedItem? {
    return world.read { get(brawler(), Equipment::class)?.get(slot) }
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

  private fun brawler(): EntityId {
    return connectionInfoService.getSelectedMasterEntityId(accountId)
  }

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 40L
    const val BLOB = 1L
    const val BRAWLER_LEVEL = 50
    const val BLOB_HEALTH = 100_000
    const val MANA = 500
    const val RUN_UP_TILES = 4
    const val OAK_STAFF = "oak_staff"
    const val OAK_STAFF_ID = 309L

    val SKILLS = listOf(
      "M_TOUGH_GUY" to 5, "M_SECOND_WIND" to 3, "M_UNBREAKABLE" to 3, "M_BATTLE_FURY" to 3, "M_STAFF_SWEEP" to 5,
      "M_RUSH" to 1,
    )
  }
}
