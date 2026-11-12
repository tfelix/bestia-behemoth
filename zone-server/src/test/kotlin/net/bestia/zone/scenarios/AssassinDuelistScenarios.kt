package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.ecs.attack.AttackTarget
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.net.AttackEntityCMSG
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.SkillExecutionService
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.casting.plagiarism.PlagiarizedSkill
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
import net.bestia.zone.master.skill.SkillListSMSG
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

/** An Assassin learns the duelist half of its tree, takes up a second dagger and casts each active over the real path. */
class AssassinDuelistScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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
  private lateinit var skillExecution: SkillExecutionService

  private lateinit var client: GameClientMock

  private var foe: EntityId? = null

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createAssassin() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    val masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "twinblade",
        hairColor = Color.WHITE,
        skinColor = Color.WHITE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id
    inventoryService.addItem(masterId, "stone_knife", 2)
    inventoryService.addItem(masterId, "basic_poison", 1)

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
  }

  /** Every Spring context shares one world and one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    client.disconnect()
    listOfNotNull(foe).forEach { id -> world.modify(id) { destroy(it) } }
  }

  @Test
  @Order(1)
  fun `without Dual Wield a second dagger is refused for the left hand`() {
    // Daggers are an Assassin's weapon; the profession is set here as the skills are below.
    world.modify(assassin()) { id -> add(id, Professions(listOf(Profession.ASSASSIN))) }

    client.sendMessage(EquipItemCMSG(accountId, freeKnife(), EquipmentSlot.RIGHT_HAND))
    await { assertNotNull(worn(EquipmentSlot.RIGHT_HAND)) }

    client.clearMessages()
    client.sendMessage(EquipItemCMSG(accountId, freeKnife(), EquipmentSlot.LEFT_HAND))

    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.EQUIP_NEEDS_DUAL_WIELD, refusal.code)
  }

  @Test
  @Order(2)
  fun `the Assassin learns the duelist skills`() {
    // Looked up first: SQL is refused inside the lease.
    val levels = SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(assassin()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      repeat(ASSASSIN_LEVEL - 1) { get(id, Level::class)!!.inc() }
      get(id, Health::class)!!.apply { max = ASSASSIN_HEALTH; current = ASSASSIN_HEALTH }
      add(id, IsStatusValueDirty)
    }

    val at = positionOf(assassin())
    // Loads the learnset now: the spawner reads it inside the lease, where SQL is refused.
    catalogue.learnset(BLOB)
    val blob = world.read { spawner.spawnMob(this, BLOB, at + Vec3L(1, 0, 0), persistent = false) }
    foe = blob
    // A blob that strikes back or wanders off breaks the casts that follow.
    world.modify(blob) { id ->
      remove(id, AiAgent::class)
      get(id, Health::class)!!.apply { max = BLOB_HEALTH; current = BLOB_HEALTH }
    }

    val known = world.read { get(assassin(), KnownSkills::class)!!.levels() }
    assertEquals(levels.toMap(), known.filterKeys { it in levels.toMap() })
  }

  @Test
  @Order(3)
  fun `with Dual Wield the second dagger goes into the left hand and swings after the first`() {
    client.sendMessage(EquipItemCMSG(accountId, freeKnife(), EquipmentSlot.LEFT_HAND))
    await { assertNotNull(worn(EquipmentSlot.LEFT_HAND)) }

    await {
      val combat = world.read { getOrThrow(assassin(), CombatStats::class).values }
      assertNotNull(combat.offHandWeapon)
      assertEquals(70, combat.offHandDamagePercent)
    }

    client.clearMessages()
    client.sendMessage(AttackEntityCMSG(accountId, assertNotNull(foe)))
    await { assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == foe && it.attackId == 0 }) }
    stopAttacking()
  }

  @Test
  @Order(4)
  fun `Sonic Blow strikes the wild bestia eight times`() {
    val blob = assertNotNull(foe)
    val sonicBlow = skillId("M_SONIC_BLOW").toInt()
    client.clearMessages()

    cast("M_SONIC_BLOW", target = blob)

    await {
      assertEquals(8, client.countReceived(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == sonicBlow })
    }
  }

  @Test
  @Order(5)
  fun `Enchant Poison spends the Basic Poison and a landing swing poisons the bestia`() {
    val blob = assertNotNull(foe)

    cast("M_ENCHANT_POISON")
    await { assertTrue(has(assassin(), StatusEffectId.ENCHANT_POISON)) }
    assertEquals(0, world.read { getOrThrow(assassin(), Inventory::class).getItems().count { it.itemId == BASIC_POISON } })

    // 25% of the landed swings, from both hands: a few seconds of swinging.
    client.sendMessage(AttackEntityCMSG(accountId, blob))
    await { assertTrue(has(blob, StatusEffectId.POISON)) }
    stopAttacking()
  }

  @Test
  @Order(6)
  fun `Plagiarism copies the bestia's Flame Dart, and casting the copy uses it up`() {
    val blob = assertNotNull(foe)
    val flameDart = skillId(FLAME_DART)
    client.clearMessages()

    // Half the darts are copied at Plagiarism Lv. 5; each check throws one more.
    await {
      world.read { skillExecution.executeFree(this, blob, flameDart, 1, assassin(), null) }
      assertTrue(world.read { has(assassin(), PlagiarizedSkill::class) })
    }
    await { assertTrue(client.receivedAny(SkillListSMSG::class) { list -> list.skills.any { it.skillId == flameDart && it.temporary } }) }

    client.clearMessages()
    world.modify(assassin()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    client.sendMessage(ActivateSkillCMSG(accountId, flameDart, 1, positionOf(assassin()), blob))

    await { assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == flameDart.toInt() }) }
    assertFalse(world.read { has(assassin(), PlagiarizedSkill::class) }, "the cast uses the copy up")
  }

  private fun cast(identifier: String, target: EntityId = 0L) {
    val skillId = skillId(identifier)
    world.modify(assassin()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, SKILLS.toMap().getValue(identifier), positionOf(assassin()), target))
  }

  private fun stopAttacking() {
    world.modify(assassin()) { id -> remove(id, AttackTarget::class) }
  }

  /** A stone knife in the bag that is not worn yet. */
  private fun freeKnife(): Long {
    return world.read { getOrThrow(assassin(), Inventory::class).getItems().first { it.itemId == STONE_KNIFE && !it.equipped }.playerItemId }
  }

  private fun worn(slot: EquipmentSlot): Equipment.EquippedItem? {
    return world.read { get(assassin(), Equipment::class)?.get(slot) }
  }

  private fun positionOf(entityId: EntityId): Vec3L = world.read { getOrThrow(entityId, Position::class).toVec3L() }

  private fun has(entityId: EntityId, effect: StatusEffectId): Boolean {
    return world.read { get(entityId, StatusEffects::class)?.hasEffect(effect.id) == true }
  }

  private fun skillId(identifier: String): Long = assertNotNull(skills.findByIdentifier(identifier)).id

  private fun assassin(): EntityId = connectionInfoService.getSelectedMasterEntityId(accountId)

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 37L
    const val BLOB = 1L
    const val ASSASSIN_LEVEL = 50
    const val ASSASSIN_HEALTH = 100_000
    const val BLOB_HEALTH = 100_000
    const val MANA = 500
    const val STONE_KNIFE = 103L
    const val BASIC_POISON = 111L
    const val FLAME_DART = "flame_dart"

    val SKILLS = listOf(
      "M_STRIP_EQUIPMENT" to 5, "M_DUAL_WIELD" to 5, "M_SONIC_BLOW" to 5, "M_POISON_RESEARCH" to 5,
      "M_ENCHANT_POISON" to 5, "M_PLAGIARISM" to 5,
    )
  }
}
