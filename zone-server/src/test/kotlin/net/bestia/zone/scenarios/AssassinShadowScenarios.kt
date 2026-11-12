package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.SkillCooldownService
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.control.net.MoveEntityCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.movement.ecs.Facing
import net.bestia.zone.navigation.local.LocalWalkQuery
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

/** An Assassin learns the shadow half of its tree and casts each active over the real path. */
class AssassinShadowScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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
  private lateinit var cooldowns: SkillCooldownService

  @Autowired
  private lateinit var statusEffects: StatusEffectService

  private lateinit var client: GameClientMock

  @Autowired
  private lateinit var walk: LocalWalkQuery

  private var foe: EntityId? = null

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createAssassin() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    val masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "nightblade",
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
    // Shadow Step lands only on ground the server holds walk data for, as it does around a real player.
    holdTerrain(client)
  }

  /** Every Spring context shares one world and one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    client.disconnect()
    listOfNotNull(foe).forEach { id -> world.modify(id) { destroy(it) } }
  }

  @Test
  @Order(1)
  fun `the Assassin learns the shadow skills`() {
    // Looked up first: SQL is refused inside the lease.
    val levels = SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(assassin()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      repeat(ASSASSIN_LEVEL - 1) { get(id, Level::class)!!.inc() }
      add(id, IsStatusValueDirty)
    }

    val at = positionOf(assassin())
    // Loads the learnset now: the spawner reads it inside the lease, where SQL is refused.
    catalogue.learnset(BLOB)
    val blob = world.read { spawner.spawnMob(this, BLOB, at + Vec3L(2, 0, 0), persistent = false) }
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
  @Order(2)
  fun `Hide conceals the Assassin and a recast ends it`() {
    cast("M_HIDE")
    await { assertTrue(has(assassin(), StatusEffectId.HIDING)) }

    cast("M_HIDE")
    await { assertFalse(has(assassin(), StatusEffectId.HIDING)) }
  }

  @Test
  @Order(3)
  fun `Backstab is refused out of hiding`() {
    client.clearMessages()

    cast("M_BACKSTAB", target = assertNotNull(foe))

    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.SKILL_NEEDS_HIDING, refusal.code)
  }

  @Test
  @Order(4)
  fun `cloaked, the Assassin shadow-steps behind the wild bestia and backstabs it`() {
    val blob = assertNotNull(foe)
    val behindBlob = world.read { getOrThrow(blob, Facing::class).behind(getOrThrow(blob, Position::class).toVec3L()) }

    cast("M_CLOAK")
    await { assertTrue(has(assassin(), StatusEffectId.CLOAKING)) }

    // The walk data of a held chunk is built a few ticks after it is served.
    await { assertNotNull(world.read { walk.surfaceAt(behindBlob) }) }
    cast("M_SHADOW_STEP", target = blob)
    await { assertEquals(behindBlob.x to behindBlob.y, positionOf(assassin()).let { it.x to it.y }) }
    assertTrue(has(assassin(), StatusEffectId.CLOAKING), "Shadow Step must leave the Assassin hidden")

    client.clearMessages()
    val backstab = skillId("M_BACKSTAB")
    cast("M_BACKSTAB", target = blob)

    await { assertTrue(client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == backstab.toInt() }) }
    assertFalse(has(assassin(), StatusEffectId.CLOAKING), "the strike ends the hiding")
  }

  @Test
  @Order(5)
  fun `Gut Punch stuns the wild bestia`() {
    val blob = assertNotNull(foe)
    standNextTo(blob)

    cast("M_GUT_PUNCH", target = blob)

    await { assertTrue(has(blob, StatusEffectId.STUN)) }
  }

  @Test
  @Order(6)
  fun `Equipment Coating coats the Assassin, and Strip Equipment fails against a coated foe`() {
    val blob = assertNotNull(foe)

    cast("M_EQUIPMENT_COATING")
    await { assertTrue(has(assassin(), StatusEffectId.EQUIPMENT_COATING)) }

    world.modify(blob) { id -> statusEffects.applyEffect(this, id, StatusEffectId.EQUIPMENT_COATING, level = 1) }
    standNextTo(blob)
    client.clearMessages()
    cast("M_STRIP_EQUIPMENT", target = blob)

    val refusal = awaitValue { assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.STRIP_GEAR_COATED, refusal.code)
  }

  @Test
  @Order(7)
  fun `a step gives a hiding Assassin away`() {
    val hide = skillId("M_HIDE")
    await { assertTrue(world.read { cooldowns.isReady(this, assassin(), hide) }) }
    cast("M_HIDE")
    await { assertTrue(has(assassin(), StatusEffectId.HIDING)) }

    val at = positionOf(assassin())
    val step = Vec3L(at.x - 1, at.y, at.z)
    client.sendMessage(MoveEntityCMSG(accountId, assassin(), waypoints = listOf(step), firstLeg = listOf(step)))

    await { assertFalse(has(assassin(), StatusEffectId.HIDING)) }
  }

  private fun cast(identifier: String, target: EntityId = 0L) {
    val skillId = skillId(identifier)
    world.modify(assassin()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, SKILLS.toMap().getValue(identifier), positionOf(assassin()), target))
  }

  /** Right beside the foe, on its own tile row, so its blows reach this client too. */
  private fun standNextTo(entityId: EntityId) {
    val at = positionOf(entityId)
    world.modify(assassin()) { id -> get(id, Position::class)!!.apply { x = at.x + 1; y = at.y; z = at.z } }
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
    const val LOGIN_ACCOUNT_ID = 36L
    const val BLOB = 1L
    const val ASSASSIN_LEVEL = 50
    const val BLOB_HEALTH = 100_000
    const val MANA = 500

    val SKILLS = listOf(
      "M_HIDE" to 5, "M_CLOAK" to 3, "M_BACKSTAB" to 5, "M_SHADOW_STEP" to 3,
      "M_GUT_PUNCH" to 3, "M_STRIP_EQUIPMENT" to 5, "M_EQUIPMENT_COATING" to 3,
    )
  }
}
