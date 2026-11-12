package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.attack.AttackExecutionService
import net.bestia.zone.battle.attack.BattleAttack
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.battle.damage.HitReach
import net.bestia.zone.battle.ecs.attack.AttackDelay
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.SkillCooldownService
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.ecs.core.World
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A Knight with a tower shield learns the guardian half of its tree and uses each skill over the real path. */
class KnightGuardianScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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
  private lateinit var attacks: AttackExecutionService

  @Autowired
  private lateinit var statusEffects: StatusEffectService

  @Autowired
  private lateinit var cooldowns: SkillCooldownService

  private lateinit var client: GameClientMock

  private var masterId = 0L

  private var brute: EntityId? = null

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createKnight() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "bulwark",
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
    inventoryService.addItem(masterId, TOWER_SHIELD, 1)

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
  }

  /** Every Spring context shares one world and one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    client.disconnect()
    (listOfNotNull(brute) + ownBestias()).forEach { id -> world.modify(id) { if (isAlive(id)) destroy(id) } }
  }

  @Test
  @Order(1)
  fun `the Knight takes up a tower shield and learns the guardian skills, and Auto Guard counts the shield`() {
    world.modify(knight()) { id ->
      add(id, Professions(listOf(Profession.KNIGHT)))
      repeat(KNIGHT_LEVEL - 1) { get(id, Level::class)!!.inc() }
    }
    equipShield()

    // Looked up first: SQL is refused inside the lease.
    val levels = SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(knight()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      add(id, IsStatusValueDirty)
    }

    await { assertEquals(4 * 5, combat().blockChancePercent, "Auto Guard 5 with a shield") }
  }

  @Test
  @Order(2)
  fun `Shield Wall goes up with a cast and down with the next`() {
    cast("M_SHIELD_WALL")

    await { assertTrue(has(knight(), StatusEffectId.SHIELD_WALL)) }
    await { assertEquals(35, combat().damageModifiers.takenByForm[HitForm.PHYSICAL]) }

    cast("M_SHIELD_WALL")

    await { assertFalse(has(knight(), StatusEffectId.SHIELD_WALL)) }
  }

  @Test
  @Order(3)
  fun `Devotion moves a blow on the Knight's own bestia onto the Knight`() {
    val bestia = ownBestias().single()
    val at = positionOf(knight())
    // A bestia that follows its own orders would wander out of the Devotion's reach.
    world.modify(bestia) { id ->
      remove(id, AiAgent::class)
      get(id, Position::class)!!.apply { x = at.x; y = at.y + 1; z = at.z }
    }
    val wolf = spawnBrute()

    cast("M_DEVOTION", target = bestia)
    await { assertTrue(has(bestia, StatusEffectId.DEVOTED)) }

    world.modify(bestia) { id ->
      val staged = get(id, IncomingDamage::class) ?: add(id, IncomingDamage())
      staged.add(BLOW, wolf, HitKind.physical(HitReach.MELEE))
    }

    await { assertEquals(wolf, world.read { get(knight(), TakenDamage::class)?.topSource() }) }
    val (max, current) = world.read { getOrThrow(bestia, Health::class).let { it.max to it.current } }
    assertEquals(max, current, "the blow reached the bestia")
  }

  @Test
  @Order(4)
  fun `Juggernaut shrugs off a stun, and a blow Auto Guard catches starts its clock again`() {
    cast("M_JUGGERNAUT")

    await { assertTrue(has(knight(), StatusEffectId.JUGGERNAUT)) }
    await { assertTrue(StatusEffectId.STUN in combat().statusImmunities && combat().knockbackImmune) }

    val wolf = assertNotNull(brute)
    val stunned = world.modify(knight()) { id -> statusEffects.applyEffect(this, id, StatusEffectId.STUN.id, 1, wolf) }
    assertEquals(false, stunned)

    world.modify(knight()) { id -> juggernautOf(this, id)!!.remainingSeconds = ALMOST_OVER_SECONDS }
    client.clearMessages()

    // Auto Guard catches one blow in five; the wolf swings until one is caught.
    await {
      swing(wolf, at = knight())
      assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == knight() && it.type == DamageEntitySMSG.DamageType.BLOCK })
    }
    val left = world.read { juggernautOf(this, knight())?.remainingSeconds }
    assertTrue(left != null && left > ALMOST_OVER_SECONDS, "the clock did not start again: $left")
  }

  @Test
  @Order(5)
  fun `without a shield Shield Wall is refused at the button`() {
    client.sendMessage(UnequipItemCMSG(accountId, EquipmentSlot.LEFT_HAND))
    await { assertEquals(null, worn(EquipmentSlot.LEFT_HAND)) }
    val shieldWall = skillId("M_SHIELD_WALL")
    await { assertTrue(world.read { cooldowns.isReady(this, knight(), shieldWall) }) }
    client.clearMessages()

    cast("M_SHIELD_WALL")

    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.SKILL_NEEDS_SHIELD, refusal.code)
  }

  private fun spawnBrute(): EntityId {
    val at = positionOf(knight())
    // Loads the learnset now: the spawner reads it inside the lease, where SQL is refused.
    catalogue.learnset(BLOB)
    val wolf = world.read { spawner.spawnMob(this, BLOB, at + Vec3L(1, 0, 0), persistent = false) }
    brute = wolf
    // It swings only when the test says so, and stays next to the Knight.
    world.modify(wolf) { id -> remove(id, AiAgent::class) }

    return wolf
  }

  /** One swing of [attacker] at [at], who is healed first: it may take a few dozen swings before one is caught. */
  private fun swing(attacker: EntityId, at: EntityId) {
    world.modify(attacker) { id ->
      get(at, Health::class)?.let { it.current = it.max }
      get(id, AttackDelay::class)?.remainingSeconds = 0f
      attacks.attack(this, id, at, BattleAttack.getBasicMeleeAttack())
    }
  }

  private fun juggernautOf(world: World, knight: EntityId): ActiveStatusEffect? {
    return world.get(knight, StatusEffects::class)?.activeEffects?.firstOrNull { it.definitionId == StatusEffectId.JUGGERNAUT.id }
  }

  private fun cast(identifier: String, target: EntityId = 0L) {
    val skillId = skillId(identifier)
    world.modify(knight()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, SKILLS.toMap().getValue(identifier), positionOf(knight()), target))
  }

  private fun equipShield() {
    val item = world.read {
      getOrThrow(knight(), Inventory::class).getItems().first { !it.equipped && it.itemId == TOWER_SHIELD_ID }
    }
    client.sendMessage(EquipItemCMSG(accountId, item.playerItemId, EquipmentSlot.LEFT_HAND))
    await { assertEquals(item.itemId, worn(EquipmentSlot.LEFT_HAND)?.itemId) }
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

  private fun ownBestias(): List<EntityId> {
    return world.read { OwnedBestia.ownedBy(this, masterId) }.map { it.entityId }
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
    const val LOGIN_ACCOUNT_ID = 39L
    const val BLOB = 1L
    const val KNIGHT_LEVEL = 50
    /** Small enough for a level 1 bestia and the Knight to live through. */
    const val BLOW = 10
    const val MANA = 500
    const val ALMOST_OVER_SECONDS = 6f
    const val TOWER_SHIELD = "tower_shield"
    const val TOWER_SHIELD_ID = 320L

    val SKILLS = listOf(
      "M_HEAVY_ARMOR_MASTERY" to 10, "M_SHIELD_WALL" to 5, "M_AUTO_GUARD" to 5, "M_DEVOTION" to 3,
      "M_CHARGE" to 3, "M_JUGGERNAUT" to 3,
    )
  }
}
