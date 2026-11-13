package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.cartography.chart.ChartService
import net.bestia.zone.cartography.persistence.MapChartRepository
import net.bestia.zone.casting.ScrySight
import net.bestia.zone.casting.ecs.SkillCooldowns
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.casting.scripts.master.Scry
import net.bestia.zone.control.net.MoveEntityCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.scry.ScrySightSMSG
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.MasterSpawnPointService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkSubscriptionService
import net.bestia.zone.world.stream.RemoteViewpoints
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

/** A Sage scries a charted spot and a party member over the real path, and each way back ends the sight. */
class ScryScenario : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var accountFactory: AccountFactory

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var gameClientFactory: GameClientMockFactory

  @Autowired
  private lateinit var connections: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var skills: SkillRepository

  @Autowired
  private lateinit var charts: ChartService

  @Autowired
  private lateinit var chartRepository: MapChartRepository

  @Autowired
  private lateinit var statusEffects: StatusEffectService

  @Autowired
  private lateinit var viewpoints: RemoteViewpoints

  @Autowired
  private lateinit var subscriptions: ChunkSubscriptionService

  @Autowired
  private lateinit var chunkService: ChunkService

  @Autowired
  private lateinit var worldService: WorldService

  private lateinit var sage: GameClientMock
  private var sageMasterId = 0L
  private lateinit var farSpot: Vec3L

  private val accountId get() = sage.connectedPlayerId

  @BeforeAll
  fun createSage() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    val chartsBefore = chartRepository.findAll().map { it.id }.toSet()
    sageMasterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "farsight",
        hairColor = Color.WHITE,
        skinColor = Color.WHITE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id
    // The starter chart covers kilometres around the spawn; without it nothing is charted, which the first test needs.
    chartRepository.deleteAll(chartRepository.findAll().filter { it.id !in chartsBefore })

    sage = gameClientFactory.getGameClient(accountId = account.id)
    sage.connect(sageMasterId)
    holdTerrain(sage)

    val scry = skillId("M_SCRY")
    world.modify(sageEntity()) { id -> get(id, KnownSkills::class)!!.learnOrUpdate(scry, SCRY_LEVEL) }

    val chunkWidth = chunkService.config.chunkSize / ChunkCoords.VOXELS_PER_POSITION_UNIT
    farSpot = positionOf(sageEntity()) + Vec3L(FAR_CHUNKS * chunkWidth, 0, 0)
  }

  @AfterAll
  fun leaveNothingBehind() {
    clientPlayer2.disconnect()
    sage.disconnect()
  }

  @Test
  @Order(1)
  fun `an uncharted spot is refused`() {
    castScry(farSpot)

    val refusal = awaitValue { assertNotNull(sage.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.SCRY_UNCHARTED, refusal.code)
    assertNull(viewpoints.of(accountId))
  }

  @Test
  @Order(2)
  fun `a charted spot opens a sight there, and the far terrain is streamed besides the body's`() {
    val voxel = worldService.config.voxelSize
    charts.grantStarterChart(sageMasterId, farSpot.x * voxel, farSpot.y * voxel, radiusMetres = CHART_RADIUS_METRES)

    castScry(farSpot)

    val sight = awaitValue { assertNotNull(sage.tryGetLastReceived(ScrySightSMSG::class)?.centre) }
    val offTarget = Vec3L(sight.x - farSpot.x, sight.y - farSpot.y, 0).distance(Vec3L(0, 0, 0)) * voxel
    assertTrue(offTarget <= MAX_DRIFT_METRES, "the sight opens within its drift of the spot, but was $offTarget m off")

    val farGround = chunkService.normalise(ChunkCoords.chunkOf(chunkService.config, sight))
    await { assertTrue(farGround in subscriptions.sentTo(accountId)) }
    assertTrue(has(sageEntity(), StatusEffectId.SCRYING), "the body stands in a trance")
    assertTrue(has(eye(), StatusEffectId.SCRY_EYE), "the eye at the far spot carries the spell")
    assertTrue(has(eye(), StatusEffectId.SCRY_VEIL), "and is hidden")
  }

  @Test
  @Order(3)
  fun `a step of the body ends the sight`() {
    val eye = eye()
    val at = positionOf(sageEntity())
    val step = Vec3L(at.x - 1, at.y, at.z)

    sage.sendMessage(MoveEntityCMSG(accountId, sageEntity(), waypoints = listOf(step), firstLeg = listOf(step)))

    awaitEnded()
    assertFalse(world.read { isAlive(eye) }, "the eye goes with the sight")
  }

  @Test
  @Order(4)
  fun `recasting Scry is the way back to the body`() {
    castScry(farSpot)
    awaitOpened()

    castScry(farSpot)

    awaitEnded()
  }

  @Test
  @Order(5)
  fun `a Dispel on the uncovered eye ends the sight`() {
    castScry(farSpot)
    awaitOpened()
    val eye = eye()

    // What Ruwach does to a veil within its reach, and then a Dispel no Scry outweighs.
    world.modify(eye) { id -> statusEffects.removeEffect(this, id, StatusEffectId.SCRY_VEIL.id, RemovalReason.DISPELLED) }
    world.modify(eye) { id -> statusEffects.dispel(this, id) }

    awaitEnded()
  }

  @Test
  @Order(6)
  fun `a party member is scried where they stand, with no chart`() {
    clientPlayer2.connect(testData.account2.masterIds.first())
    val memberMasterId = testData.account2.masterIds.first()
    val memberAt = positionOf(connections.getSelectedMasterEntityId(clientPlayer2.connectedPlayerId))
    val party = setOf(
      PartyMembership.Member(accountId, sageMasterId),
      PartyMembership.Member(clientPlayer2.connectedPlayerId, memberMasterId),
    )
    world.modify(sageEntity()) { id -> add(id, PartyMembership(PARTY_ID, party, PartyExpShare.EACH_TAKE)) }

    castScry(Vec3L(0, 0, 0), args = listOf(Scry.PARTY_MEMBER, memberMasterId))

    val sight = awaitValue { assertNotNull(sage.tryGetLastReceived(ScrySightSMSG::class)?.centre) }
    val offTarget = Vec3L(sight.x - memberAt.x, sight.y - memberAt.y, 0).distance(Vec3L(0, 0, 0)) * worldService.config.voxelSize
    assertTrue(offTarget <= MAX_DRIFT_METRES)

    castScry(farSpot)
    awaitEnded()
    world.modify(sageEntity()) { id -> remove(id, PartyMembership::class) }
  }

  private fun castScry(at: Vec3L, args: List<Long> = emptyList()) {
    sage.clearMessages()
    world.modify(sageEntity()) { id ->
      get(id, Mana::class)!!.apply { max = MANA; current = MANA }
      remove(id, SkillCooldowns::class)
    }
    sage.sendMessage(ActivateSkillCMSG(accountId, skillId("M_SCRY"), SCRY_LEVEL, at, targetEntityId = 0L, args = args))
  }

  private fun awaitOpened() {
    await { assertNotNull(sage.tryGetLastReceived(ScrySightSMSG::class)?.centre) }
  }

  private fun awaitEnded() {
    await { assertTrue(sage.receivedAny(ScrySightSMSG::class) { it.centre == null }) }
    assertNull(viewpoints.of(accountId), "the far terrain is no longer streamed")
    assertFalse(has(sageEntity(), StatusEffectId.SCRYING))
  }

  private fun eye(): EntityId {
    return awaitValue { assertNotNull(world.read { get(sageEntity(), ScrySight::class)?.eyeId }) }
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

  private fun sageEntity(): EntityId {
    return connections.getSelectedMasterEntityId(accountId)
  }

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 61L
    const val SCRY_LEVEL = 5
    const val MANA = 500
    const val FAR_CHUNKS = 15L
    const val CHART_RADIUS_METRES = 800.0
    const val MAX_DRIFT_METRES = 500.0
    const val PARTY_ID = 9_061L
  }
}
