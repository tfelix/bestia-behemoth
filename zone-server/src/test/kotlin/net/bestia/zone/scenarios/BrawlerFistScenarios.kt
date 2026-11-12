package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.ecs.attack.AttackTarget
import net.bestia.zone.battle.ecs.attack.ComboOpening
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.net.AttackEntityCMSG
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.equip.EquipmentSlot
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
import kotlin.test.assertTrue

/** A bare-handed Brawler learns the fist half of its tree and uses each skill over the real path. */
class BrawlerFistScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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

  private lateinit var client: GameClientMock

  private var dummy: EntityId? = null

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createBrawler() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    val masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "ironknuckle",
        hairColor = Color.WHITE,
        skinColor = Color.WHITE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
  }

  /** Every Spring context shares one world and one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    client.disconnect()
    listOfNotNull(dummy).forEach { id -> world.modify(id) { if (isAlive(id)) destroy(id) } }
  }

  @Test
  @Order(1)
  fun `the Brawler puts the knife away and learns the fist skills, and Iron Fist hardens its bare hands`() {
    world.modify(brawler()) { id ->
      add(id, Professions(listOf(Profession.BRAWLER)))
      repeat(BRAWLER_LEVEL - 1) { get(id, Level::class)!!.inc() }
    }
    client.sendMessage(UnequipItemCMSG(accountId, EquipmentSlot.RIGHT_HAND))
    await { assertEquals(null, worn(EquipmentSlot.RIGHT_HAND)) }
    await { assertEquals(null, combat().heldWeaponType) }
    val bareAtk = combat().weapon.atk

    // Looked up first: SQL is refused inside the lease.
    val levels = SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(brawler()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      add(id, IsStatusValueDirty)
    }

    await { assertEquals(bareAtk + IRON_FIST_ATK, combat().weapon.atk, "Iron Fist 10") }

    val at = positionOf(brawler())
    // Loads the learnset now: the spawner reads it inside the lease, where SQL is refused.
    catalogue.learnset(BLOB)
    val blob = world.read { spawner.spawnMob(this, BLOB, at + Vec3L(1, 0, 0), persistent = false) }
    dummy = blob
    // A blob that strikes back or wanders off breaks the blows that follow.
    world.modify(blob) { id ->
      remove(id, AiAgent::class)
      get(id, Health::class)!!.apply { max = BLOB_HEALTH; current = BLOB_HEALTH }
    }
  }

  @Test
  @Order(2)
  fun `a basic attack sooner or later lands as a Triple Blow, and opens a Chain Combo`() {
    val blob = assertNotNull(dummy)
    client.clearMessages()

    // 30% of the landed swings at Lv. 10: a few seconds of swinging.
    client.sendMessage(AttackEntityCMSG(accountId, blob))
    await {
      assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == 0 && it.div == 3 })
    }
    stopAttacking()

    assertTrue(world.read { has(brawler(), ComboOpening::class) })
  }

  @Test
  @Order(3)
  fun `without a Triple Blow just before, Chain Combo is refused at the button`() {
    val blob = assertNotNull(dummy)
    world.modify(brawler()) { id -> get(id, ComboOpening::class)!!.closesAtMillis = 0 }
    client.clearMessages()

    cast("M_CHAIN_COMBO", target = blob)

    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.SKILL_NEEDS_TRIPLE_BLOW, refusal.code)
  }

  @Test
  @Order(4)
  fun `right after a Triple Blow, Chain Combo strikes the bestia four times`() {
    val blob = assertNotNull(dummy)
    val chainCombo = skillId("M_CHAIN_COMBO").toInt()
    // A wider opening than a Triple Blow leaves, so a slow test machine cannot miss it.
    world.modify(brawler()) { id -> get(id, ComboOpening::class)!!.closesAtMillis = System.currentTimeMillis() + 60_000 }
    client.clearMessages()

    cast("M_CHAIN_COMBO", target = blob)

    await {
      assertEquals(4, client.countReceived(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == chainCombo })
    }
  }

  @Test
  @Order(5)
  fun `Ultima Fist is refused at the button while the Brawler is not Berserk`() {
    val blob = assertNotNull(dummy)
    client.clearMessages()

    cast("M_ULTIMA_FIST", target = blob)

    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.SKILL_NEEDS_BERSERK, refusal.code)
  }

  @Test
  @Order(6)
  fun `Berserk, Ultima Fist spends all mana on one blow, ends the Berserk and seals the mana`() {
    val blob = assertNotNull(dummy)
    val ultimaFist = skillId("M_ULTIMA_FIST").toInt()
    cast("M_BATTLE_FURY")
    await { assertTrue(has(brawler(), StatusEffectId.BERSERK)) }
    client.clearMessages()

    cast("M_ULTIMA_FIST", target = blob)

    await { assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == ultimaFist }) }
    await { assertTrue(has(brawler(), StatusEffectId.MANA_SEALED)) }
    assertFalse(has(brawler(), StatusEffectId.BERSERK))
    // A regeneration tick may come before the seal does, so only "almost nothing" holds.
    val mana = world.read { getOrThrow(brawler(), Mana::class).current }
    assertTrue(mana < MANA / 10, "mana $mana")
  }

  private fun cast(identifier: String, target: EntityId = 0L) {
    val skillId = skillId(identifier)
    world.modify(brawler()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, SKILLS.toMap().getValue(identifier), positionOf(brawler()), target))
  }

  private fun stopAttacking() {
    world.modify(brawler()) { id -> remove(id, AttackTarget::class) }
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
    const val LOGIN_ACCOUNT_ID = 41L
    const val BLOB = 1L
    const val BRAWLER_LEVEL = 50
    const val BLOB_HEALTH = 1_000_000
    const val MANA = 500
    const val IRON_FIST_ATK = 30

    val SKILLS = listOf(
      "M_IRON_FIST" to 10, "M_TRIPLE_BLOW" to 10, "M_CHAIN_COMBO" to 5, "M_BATTLE_FURY" to 3, "M_ULTIMA_FIST" to 5,
    )
  }
}
