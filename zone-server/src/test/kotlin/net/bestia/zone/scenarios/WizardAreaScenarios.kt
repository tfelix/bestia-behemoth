package net.bestia.zone.scenarios

import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.StatusEffectService
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
import net.bestia.zone.casting.SkillCooldownService
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

/** A Wizard learns the elemental area spells and casts each over the real path at a wild bestia. */
class WizardAreaScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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

  private var foe: EntityId? = null

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createWizard() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    val masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "elementalist",
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
    val wizard = wizard()
    val areas = areasWhere { it.casterId == wizard }
    client.disconnect()
    (areas + listOfNotNull(foe)).forEach { id -> world.modify(id) { destroy(it) } }
  }

  @Test
  @Order(1)
  fun `the Wizard learns the elemental area spells`() {
    // Looked up first: SQL is refused inside the lease.
    val levels = SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(wizard()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      repeat(WIZARD_LEVEL - 1) { get(id, Level::class)!!.inc() }
      add(id, IsStatusValueDirty)
    }

    val at = world.read { getOrThrow(wizard(), Position::class).toVec3L() }
    // Loads the learnset now: the spawner reads it inside the lease, where SQL is refused.
    catalogue.learnset(BLOB)
    val blob = world.read { spawner.spawnMob(this, BLOB, at + Vec3L(3, 0, 0), persistent = false) }
    foe = blob
    // A blob that strikes back breaks every cast bar after the first hit.
    world.modify(blob) { id -> remove(id, AiAgent::class) }

    val known = world.read { get(wizard(), KnownSkills::class)!!.levels() }
    assertEquals(levels.toMap(), known.filterKeys { it in levels.toMap() })
  }

  @Test
  @Order(2)
  fun `Quagmire strips Increase AGI from the wild bestia and halves its AGI at Lv3`() {
    val blob = assertNotNull(foe)
    world.modify(blob) { id -> statusEffects.applyEffect(this, id, StatusEffectId.INCREASE_AGI, level = 1) }
    await { assertTrue(has(blob, StatusEffectId.INCREASE_AGI)) }

    cast("M_QUAGMIRE", at = positionOf(blob))

    await { assertTrue(has(blob, StatusEffectId.QUAGMIRE)) }
    await { assertFalse(has(blob, StatusEffectId.INCREASE_AGI)) }
    await {
      val values = world.read { get(blob, CombatStats::class)!!.values }
      assertEquals(values.base.agility * 50 / 100, values.attributes.agility)
    }
  }

  @Test
  @Order(3)
  fun `Meteor Storm, Storm Gust, Fire Wall and Earthquake each strike the wild bestia`() {
    val blob = assertNotNull(foe)

    // Fire Wall last of the three: it throws the blob a few tiles, out from under a storm aimed where it stood.
    for (identifier in listOf("M_METEOR_STORM", "M_STORM_GUST", "M_FIRE_WALL")) {
      castAndAwaitBlow(identifier, blob, at = positionOf(blob))
    }

    // The throw may have put the blob in a chunk the client does not hold, where it would hear of no blow.
    val wizardAt = positionOf(wizard())
    world.modify(blob) { id -> get(id, Position::class)!!.apply { x = wizardAt.x + 1; y = wizardAt.y; z = wizardAt.z } }
    castAndAwaitBlow("M_EARTHQUAKE", blob)
  }

  @Test
  @Order(4)
  fun `Thunder Storm gathers over the wild bestia and Frost Nova bursts around the Wizard`() {
    val blob = assertNotNull(foe)

    // Its strikes land at random, so that the storm stands is what can be asserted.
    cast("M_THUNDER_STORM", at = positionOf(blob))
    await { assertTrue(areasOf("M_THUNDER_STORM").isNotEmpty()) }

    // A freeze is a roll, and the burst is gone half a second later: the cooldown says it resolved.
    val frostNova = skillId("M_FROST_NOVA")
    cast("M_FROST_NOVA")
    await { assertFalse(world.read { cooldowns.isReady(this, wizard(), frostNova) }) }
  }

  private fun castAndAwaitBlow(identifier: String, blob: EntityId, at: Vec3L? = null) {
    val skillId = skillId(identifier)
    // Enough to survive every tick: a dead blob would fizzle the next cast.
    world.modify(blob) { id -> get(id, Health::class)!!.apply { max = BLOB_HEALTH; current = BLOB_HEALTH } }
    client.clearMessages()

    cast(identifier, at)

    await {
      val struck = client.receivedAny(DamageEntitySMSG::class) { it.entityId == blob && it.attackId == skillId.toInt() }
      assertTrue(struck, "$identifier did not strike the blob")
    }
  }

  private fun cast(identifier: String, at: Vec3L? = null) {
    val skillId = skillId(identifier)
    world.modify(wizard()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    val aimedAt = at ?: positionOf(wizard())
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, SKILLS.toMap().getValue(identifier), aimedAt, 0L))
  }

  private fun positionOf(entityId: EntityId): Vec3L = world.read { getOrThrow(entityId, Position::class).toVec3L() }

  private fun areasOf(identifier: String): List<EntityId> {
    val skillId = skillId(identifier)

    return areasWhere { it.skillId == skillId }
  }

  private fun areasWhere(predicate: (AreaEffect) -> Boolean): List<EntityId> {
    val found = mutableListOf<EntityId>()
    world.read { query(AreaEffect::class).each { id -> if (predicate(get<AreaEffect>())) found.add(id) } }

    return found
  }

  private fun has(entityId: EntityId, effect: StatusEffectId): Boolean {
    return world.read { get(entityId, StatusEffects::class)?.hasEffect(effect.id) == true }
  }

  private fun skillId(identifier: String): Long = assertNotNull(skills.findByIdentifier(identifier)).id

  private fun wizard(): EntityId = connectionInfoService.getSelectedMasterEntityId(accountId)

  private companion object {
    const val LOGIN_ACCOUNT_ID = 35L
    const val BLOB = 1L
    const val WIZARD_LEVEL = 50
    const val BLOB_HEALTH = 100_000
    const val MANA = 500

    val SKILLS = listOf(
      "M_FIREBOLT" to 5, "M_ICE_BOLT" to 5, "M_THUNDER_BOLT" to 3, "M_EARTH_SPIKE" to 3,
      "M_FIRE_WALL" to 3, "M_METEOR_STORM" to 3, "M_STORM_GUST" to 3, "M_FROST_NOVA" to 3,
      "M_QUAGMIRE" to 3, "M_THUNDER_STORM" to 3, "M_EARTHQUAKE" to 3,
    )
  }
}
