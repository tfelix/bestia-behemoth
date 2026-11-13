package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillCooldownService
import net.bestia.zone.casting.net.ActivateSkillCMSG
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
import net.bestia.zone.movement.ecs.Speed
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A Bard with a lute learns the songs and plays each over the real path: the field reaches the Bard's own bestia but
 * not a wild blob, several songs play at once, a song outlasts the field by a few seconds, and putting the lute away
 * ends them all.
 */
class BardSongScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

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
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var cooldowns: SkillCooldownService

  @Autowired
  private lateinit var visibility: EntityVisibility

  @Autowired
  private lateinit var playerBestiaCreateOperation: PlayerBestiaCreateOperation

  private lateinit var client: GameClientMock

  /** The Bard before any song. */
  private lateinit var unsung: CombatValues
  private var unsungSpeed = 0f

  private var masterId = 0L

  private var blob: EntityId? = null

  private val accountId get() = client.connectedPlayerId

  @BeforeAll
  fun createBard() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "songbird",
        hairColor = Color.WHITE,
        skinColor = Color.WHITE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id
    // Before connecting: the live bag and bestias are read from the database when the master enters the world.
    playerBestiaCreateOperation.createAndSpawn(masterId, PlayerBestiaCreateData("blob", Vec3L.ZERO))
    inventoryService.addItem(masterId, WOODEN_LUTE, 1)

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
    holdTerrain(client)
  }

  /** Every Spring context shares one world and one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    client.disconnect()
    (listOfNotNull(blob) + ownBestia()).forEach { id -> world.modify(id) { if (isAlive(id)) destroy(id) } }
  }

  @Test
  @Order(1)
  fun `the Bard takes up a lute and learns the songs`() {
    world.modify(bard()) { id ->
      add(id, Professions(listOf(Profession.BARD)))
      repeat(BARD_LEVEL - 1) { get(id, Level::class)!!.inc() }
    }
    // A lute needs both hands, and the starter kit fills the left one.
    client.sendMessage(UnequipItemCMSG(accountId, EquipmentSlot.LEFT_HAND))
    await { assertNull(world.read { get(bard(), Equipment::class)?.get(EquipmentSlot.LEFT_HAND) }) }
    equipLute()
    await { assertEquals(WeaponType.INSTRUMENT, combat().heldWeaponType) }

    // Looked up first: SQL is refused inside the lease.
    val levels = SKILLS.map { (identifier, level) -> skillId(identifier) to level }
    world.modify(bard()) { id ->
      val known = get(id, KnownSkills::class)!!
      levels.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      add(id, IsStatusValueDirty)
    }
    val grandPerformance = skillId("M_GRAND_PERFORMANCE")
    await { assertEquals(3, world.read { getOrThrow(bard(), KnownSkills::class).levelOf(grandPerformance) }) }
    unsung = combat()
    unsungSpeed = world.read { getOrThrow(bard(), Speed::class).speed }
  }

  @Test
  @Order(2)
  fun `Battle Hymn reaches the Bard and the Bard's bestia within 5 tiles, not a wild blob`() {
    val bestia = placeOwnBestia(Vec3L(0, 4, 0))
    val wild = spawnBlob(Vec3L(1, 0, 0))

    cast("M_BATTLE_HYMN")

    await { assertTrue(has(bestia, StatusEffectId.BATTLE_HYMN), "Voice Training 2 reaches 5 tiles") }
    assertTrue(has(bard(), StatusEffectId.BATTLE_HYMN), "the Bard hears the own song")
    assertFalse(has(wild, StatusEffectId.BATTLE_HYMN), "never the other side")
    // Grand Performance 3: 15% becomes 19%.
    await { assertEquals(unsung.weapon.atk * 119 / 100, combat().weapon.atk) }
    val song = assertNotNull(effectOn(bard(), StatusEffectId.SONG_BATTLE_HYMN))
    assertTrue(song.remainingSeconds > 60f, "Voice Training 2 holds it 72 s, was ${song.remainingSeconds}")
  }

  @Test
  @Order(3)
  fun `War March, Mirage Dance and Wanderer's Tune play beside Battle Hymn`() {
    // One cast at a time: the next song is refused while the last is still being played up.
    listOf(
      "M_WAR_MARCH" to StatusEffectId.SONG_WAR_MARCH,
      "M_MIRAGE_DANCE" to StatusEffectId.SONG_MIRAGE_DANCE,
      "M_WANDERERS_TUNE" to StatusEffectId.SONG_WANDERERS_TUNE,
    ).forEach { (song, field) ->
      cast(song)
      await { assertTrue(has(bard(), field), "$song plays") }
    }

    await {
      val bard = combat()
      assertEquals(13, bard.aspdPercent, "War March 5 with Grand Performance 3")
      assertEquals(unsung.derived.flee + 19, bard.derived.flee, "Mirage Dance 5 with Grand Performance 3")
      assertEquals(65, bard.staminaDrainReduction, "Wanderer's Tune 5 with Grand Performance 3")
    }
    assertTrue(world.read { getOrThrow(bard(), Speed::class).speed } > unsungSpeed, "War March is faster on foot")
    assertTrue(has(bard(), StatusEffectId.BATTLE_HYMN), "a new song stops no other")
    val tune = assertNotNull(effectOn(bard(), StatusEffectId.SONG_WANDERERS_TUNE))
    assertTrue(tune.remainingSeconds > 600f, "Wanderer's Tune plays 10 min and more, was ${tune.remainingSeconds}")
  }

  @Test
  @Order(4)
  fun `a bestia that walks out of the field keeps the song a few seconds, then loses it`() {
    val bestia = placeOwnBestia(Vec3L(0, 20, 0))

    assertTrue(has(bestia, StatusEffectId.BATTLE_HYMN), "it lingers")
    await { assertFalse(has(bestia, StatusEffectId.BATTLE_HYMN)) }
    assertTrue(has(bard(), StatusEffectId.BATTLE_HYMN), "the Bard still plays")
  }

  @Test
  @Order(5)
  fun `putting the lute away ends every song, and a song needs an instrument`() {
    client.sendMessage(UnequipItemCMSG(accountId, EquipmentSlot.RIGHT_HAND))

    await {
      SONG_FIELDS.forEach { assertFalse(has(bard(), it), "$it still plays") }
    }
    awaitReady(skillId("M_BATTLE_HYMN"))
    client.clearMessages()
    cast("M_BATTLE_HYMN")

    var refusal: OperationErrorSMSG? = null
    await { refusal = assertNotNull(client.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.SKILL_NEEDS_INSTRUMENT, refusal!!.code)
  }

  /** The Bard's own bestia at [offset] from the Bard, standing still. */
  private fun placeOwnBestia(offset: Vec3L): EntityId {
    val bestia = ownBestia().single()
    val at = positionOf(bard()) + offset
    // A bestia that follows its own orders would walk back to its master.
    world.modify(bestia) { id ->
      remove(id, AiAgent::class)
      get(id, Position::class)!!.apply { x = at.x; y = at.y; z = at.z }
    }

    return bestia
  }

  /** A wild blob next to the Bard that neither fights back nor wanders. */
  private fun spawnBlob(offset: Vec3L): EntityId {
    val at = positionOf(bard()) + offset
    val spawned = world.read { spawner.spawnMob(this, BLOB, at, persistent = false) }
    blob = spawned
    world.modify(spawned) { id ->
      remove(id, AiAgent::class)
      get(id, Health::class)!!.apply { max = BLOB_HEALTH; current = BLOB_HEALTH }
    }
    // Only an entity a tick has placed in its chunk is found around the Bard.
    await { assertTrue(accountId in visibility.observersOf(spawned)) }

    return spawned
  }

  private fun cast(identifier: String) {
    val skillId = skillId(identifier)
    world.modify(bard()) { id -> get(id, Mana::class)!!.apply { max = MANA; current = MANA } }
    client.sendMessage(ActivateSkillCMSG(accountId, skillId, SKILLS.toMap().getValue(identifier), positionOf(bard()), bard()))
  }

  private fun awaitReady(skillId: Long) {
    await { assertTrue(world.read { cooldowns.isReady(this, bard(), skillId) }) }
  }

  private fun equipLute() {
    val lute = world.read {
      getOrThrow(bard(), Inventory::class).getItems().first { !it.equipped && it.itemId == WOODEN_LUTE_ID }
    }
    client.clearMessages()
    client.sendMessage(EquipItemCMSG(accountId, lute.playerItemId, EquipmentSlot.RIGHT_HAND))
    await {
      assertEquals(
        WOODEN_LUTE_ID,
        world.read { get(bard(), Equipment::class)?.get(EquipmentSlot.RIGHT_HAND)?.itemId },
        "lute refused: ${client.tryGetLastReceived(OperationErrorSMSG::class)?.code}"
      )
    }
  }

  private fun effectOn(entityId: EntityId, effect: StatusEffectId): ActiveStatusEffect? {
    return world.read { get(entityId, StatusEffects::class)?.activeEffects?.firstOrNull { it.definitionId == effect.id } }
  }

  private fun has(entityId: EntityId, effect: StatusEffectId): Boolean {
    return effectOn(entityId, effect) != null
  }

  private fun combat(): CombatValues {
    return world.read { getOrThrow(bard(), CombatStats::class).values }
  }

  private fun positionOf(entityId: EntityId): Vec3L {
    return world.read { getOrThrow(entityId, Position::class).toVec3L() }
  }

  private fun ownBestia(): List<EntityId> {
    return world.read { OwnedBestia.ownedBy(this, masterId) }.map { it.entityId }
  }

  private fun skillId(identifier: String): Long {
    return assertNotNull(skills.findByIdentifier(identifier)).id
  }

  private fun bard(): EntityId {
    return connectionInfoService.getSelectedMasterEntityId(accountId)
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 47L
    const val BLOB = 1L
    const val BARD_LEVEL = 50
    const val BLOB_HEALTH = 100_000
    const val MANA = 500

    const val WOODEN_LUTE = "wooden_lute"
    const val WOODEN_LUTE_ID = 313L

    val SKILLS = listOf(
      "M_VOICE_TRAINING" to 2, "M_BATTLE_HYMN" to 5, "M_WAR_MARCH" to 5, "M_MIRAGE_DANCE" to 5,
      "M_WANDERERS_TUNE" to 5, "M_GRAND_PERFORMANCE" to 3,
    )

    val SONG_FIELDS = listOf(
      StatusEffectId.SONG_BATTLE_HYMN, StatusEffectId.SONG_WAR_MARCH, StatusEffectId.SONG_MIRAGE_DANCE,
      StatusEffectId.SONG_WANDERERS_TUNE,
    )
  }
}
