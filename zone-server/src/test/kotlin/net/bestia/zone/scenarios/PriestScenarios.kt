package net.bestia.zone.scenarios

import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.master.MasterFactory
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

/** A Priest casts each of its blessings over the real message path and the tick. */
class PriestScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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
  private lateinit var statusEffects: StatusEffectService

  @Autowired
  private lateinit var spawner: BestiaEntitySpawner

  @Autowired
  private lateinit var catalogue: BestiaCatalogue

  private lateinit var client: GameClientMock

  private var foe: EntityId? = null

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createPriest() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    val masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "priest",
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
    foe?.let { id -> world.modify(id) { destroy(it) } }
  }

  @Test
  @Order(1)
  fun `the Priest learns every blessing`() {
    // Looked up first: SQL is refused inside the lease.
    val levels = PRIEST_SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(priest()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      // A new master's mana pays for one blessing at most; a higher level gives it room for Heal Lv10.
      repeat(PRIEST_LEVEL - 1) { get(id, Level::class)!!.inc() }
      add(id, IsStatusValueDirty)
    }
    await { assertTrue(world.read { get(priest(), Mana::class)!!.max } > MANA_NEEDED) }

    val gloria = skillId("M_GLORIA")
    assertTrue(world.read { get(priest(), KnownSkills::class)!!.knowsSkill(gloria, 1) })
  }

  @Test
  @Order(2)
  fun `Increase AGI, Kyrie Eleison, Magnificat and Gloria land on the Priest`() {
    for ((identifier, effect) in SELF_BUFFS) {
      cast(identifier)
      await { assertTrue(has(priest(), effect), "$identifier gives ${effect.name}") }
    }
  }

  @Test
  @Order(3)
  fun `Suffragium shortens the next cast bar and is spent by it`() {
    cast("M_SUFFRAGIUM")
    await { assertEquals(30, world.read { get(priest(), CombatStats::class)!!.values.castTimeReduction }) }

    cast("M_BLESSING")

    await { assertEquals(1.4f, world.read { get(priest(), Casting::class)?.totalSeconds }) }
    assertFalse(has(priest(), StatusEffectId.SUFFRAGIUM))
    await { assertTrue(has(priest(), StatusEffectId.BLESSING)) }
  }

  @Test
  @Order(4)
  fun `Status Recovery lifts Blind`() {
    world.modify(priest()) { id -> statusEffects.applyEffect(this, id, StatusEffectId.BLIND, 1) }
    assertTrue(has(priest(), StatusEffectId.BLIND))

    cast("M_STATUS_RECOVERY")

    await { assertFalse(has(priest(), StatusEffectId.BLIND)) }
  }

  @Test
  @Order(5)
  fun `Heal mends the Priest`() {
    world.modify(priest()) { id -> get(id, Health::class)!!.current = 1 }

    cast("M_HEAL", level = 10)

    await {
      val heal = client.getLastReceived(DamageEntitySMSG::class)
      assertEquals(DamageEntitySMSG.DamageType.HEAL, heal.type)
      assertEquals(priest(), heal.entityId)
    }
  }

  @Test
  @Order(6)
  fun `Signum Crucis and Lex Aeterna mark a wild bestia`() {
    val at = world.read { getOrThrow(priest(), Position::class).toVec3L() }
    // Loads the learnset now: the spawner reads it inside the lease, where SQL is refused.
    catalogue.learnset(BLOB)
    val blob = world.read { spawner.spawnMob(this, BLOB, at + Vec3L(3, 0, 0), persistent = false) }
    foe = blob

    cast("M_SIGNUM_CRUCIS", level = 3, target = blob)
    await { assertTrue(has(blob, StatusEffectId.SIGNUM_CRUCIS)) }

    cast("M_LEX_AETERNA", target = blob)
    await { assertTrue(has(blob, StatusEffectId.LEX_AETERNA)) }
  }

  private fun cast(identifier: String, level: Int = 1, target: EntityId = 0L) {
    val skillId = skillId(identifier)
    world.modify(priest()) { id -> get(id, Mana::class)!!.apply { current = max } }
    val at = world.read { getOrThrow(priest(), Position::class).toVec3L() }
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, level, at, target))
  }

  private fun has(entityId: EntityId, effect: StatusEffectId): Boolean {
    return world.read { get(entityId, StatusEffects::class)?.hasEffect(effect.id) == true }
  }

  private fun skillId(identifier: String): Long = assertNotNull(skills.findByIdentifier(identifier)).id

  private fun priest(): EntityId = connectionInfoService.getSelectedMasterEntityId(accountId)

  private companion object {
    const val LOGIN_ACCOUNT_ID = 32L
    const val BLOB = 1L
    const val PRIEST_LEVEL = 50

    /** Heal at Lv10, the dearest cast here. */
    const val MANA_NEEDED = 40

    val PRIEST_SKILLS = listOf(
      "M_HEAL" to 10, "M_BLESSING" to 5, "M_INCREASE_AGI" to 3, "M_KYRIE_ELEISON" to 3, "M_SUFFRAGIUM" to 1,
      "M_MAGNIFICAT" to 3, "M_SIGNUM_CRUCIS" to 3, "M_STATUS_RECOVERY" to 1, "M_LEX_AETERNA" to 1, "M_GLORIA" to 1,
    )

    val SELF_BUFFS = listOf(
      "M_INCREASE_AGI" to StatusEffectId.INCREASE_AGI,
      "M_KYRIE_ELEISON" to StatusEffectId.KYRIE_ELEISON,
      "M_MAGNIFICAT" to StatusEffectId.MAGNIFICAT,
      "M_GLORIA" to StatusEffectId.GLORIA,
    )
  }
}
