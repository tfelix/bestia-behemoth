package net.bestia.zone.companion.query

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.MapPoint
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneAnswer.Theodolites.Theodolite
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.account.persistence.LearnedSkill
import net.bestia.zone.account.persistence.LearnedSkillRepository
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.cartography.survey.SurveyMinigame
import net.bestia.zone.cartography.survey.SurveyTier
import net.bestia.zone.cartography.survey.TheodoliteSurveys
import net.bestia.zone.casting.ReagentPayment
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.skill.persistence.SkillMechanics
import net.bestia.zone.skill.persistence.SkillReagent
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.util.AccountId
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertIs
import net.bestia.zone.identity.ecs.Master as MasterComponent

/** Surveys from the companion app keep the rules of a Cartography cast: own theodolite, level, paper and ink. */
class SurveyCommandsTest {

  private val world = testWorld()
  private val owned = mockk<OwnedMasters>()
  private val structures = mockk<PlayerStructureRegistry>(relaxed = true)
  private val learnedSkills = mockk<LearnedSkillRepository>()
  private val inventoryService = mockk<InventoryService>()
  private val asyncJobs = mockk<AsyncJobExecutor> { every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() } }
  private val worldService = mockk<WorldService> { every { config.voxelSize } returns 2.0 }

  private val cartography = mockk<Skill> {
    every { id } returns CARTOGRAPHY_ID
    every { mechanics } returns SkillMechanics(reagents = listOf(SkillReagent("chart_blank", 1, PAPER), SkillReagent("ink", 1, INK)))
  }
  private val skills = mockk<SkillRepository> { every { findByIdentifier("M_CARTOGRAPHY") } returns cartography }

  private val surveys = TheodoliteSurveys(
    structures = structures,
    chartService = mockk { every { inventoryCoverage(MASTER_ID) } returns mockk { every { contains(any(), any()) } returns false } },
    surveyService = mockk(relaxed = true),
    mana = mockk { every { manaAt(any()) } returns 0.5 },
    worldService = worldService,
    asyncJobExecutor = asyncJobs,
    messages = mockk(relaxed = true),
  )

  /** Runs IO work at once, as if the account's inbox had nothing else queued. */
  private val inbox = object : AccountTaskExecutor {
    override fun onTick(accountId: AccountId, task: World.() -> Unit): CompletableFuture<Unit> {
      error("app surveys run on the IO lane")
    }

    override fun onIo(accountId: AccountId, task: () -> Unit): CompletableFuture<Unit> {
      return CompletableFuture.completedFuture(task())
    }
  }

  private val commands = SurveyCommands(
    owned, structures, surveys, skills, learnedSkills, ReagentPayment(inventoryService, asyncJobs), inventoryService,
    world, MapPlane(worldService), inbox,
  )

  private val master = mockk<Master> {
    every { id } returns MASTER_ID
    every { entityId } returns MASTER_ENTITY
  }

  private val idle = theodolite(id = 7)

  @Test
  fun `the list shows the master's standing theodolites and their state`() {
    givenOwned()
    every { structures.all() } returns listOf(
      theodolite(id = 9, surveyRadiusMetres = 3000, surveyRemainingSeconds = 419.5f),
      idle,
      theodolite(id = 8, surveyRadiusMetres = 5000),
      theodolite(id = 10, totalBuildSeconds = 60f),
      theodolite(id = 11, ownerMasterId = MASTER_ID + 1),
      theodolite(id = 12, kind = StaticEntityKind.WORKBENCH),
    )

    val answer = commands.list("c", ACCOUNT_ID, ZoneQuery.Theodolites(MASTER_ID)).answer

    assertEquals(
      ZoneAnswer.Theodolites(
        listOf(
          Theodolite(7, MapPoint(20.0, 40.0), Theodolite.IDLE, null, null),
          Theodolite(8, MapPoint(20.0, 40.0), Theodolite.READY, 5, null),
          Theodolite(9, MapPoint(20.0, 40.0), Theodolite.SURVEYING, 3, 420),
        )
      ),
      answer,
    )
  }

  @Test
  fun `someone else's master has no theodolites to show or start`() {
    every { owned.find(ACCOUNT_ID, MASTER_ID) } returns null

    assertEquals(Status.NOT_FOUND, commands.list("c", ACCOUNT_ID, ZoneQuery.Theodolites(MASTER_ID)).status)
    assertEquals(Status.NOT_FOUND, start().status)
    verify(exactly = 0) { inventoryService.consumeAll(any(), any(), any()) }
  }

  @Test
  fun `another master's theodolite is not found`() {
    givenOwned()
    givenStoredLevel(5)
    every { structures.of(7) } returns theodolite(id = 7, ownerMasterId = MASTER_ID + 1)

    assertEquals(Status.NOT_FOUND, start().status)
  }

  @Test
  fun `a size above the master's Cartography level is locked`() {
    givenOwned()
    givenIdleTheodolite()
    givenStoredLevel(2)

    assertRefused(OpError.SURVEY_TIER_LOCKED, start(km = 3))
    verify(exactly = 0) { inventoryService.consumeAll(any(), any(), any()) }
  }

  @Test
  fun `a theodolite that already surveys cannot start another`() {
    givenOwned()
    givenStoredLevel(1)
    every { structures.of(7) } returns theodolite(id = 7, surveyRadiusMetres = 1000, surveyRemainingSeconds = 300f)

    assertRefused(OpError.SURVEY_NO_THEODOLITE, start())
  }

  @Test
  fun `a master away without paper or ink cannot start`() {
    givenOwned()
    givenIdleTheodolite()
    givenStoredLevel(1)
    every { inventoryService.consumeAll(MASTER_ID, listOf(PAPER to 1, INK to 1)) } returns false

    assertRefused(OpError.SKILL_MISSING_REAGENT, start())
  }

  @Test
  fun `a master away pays from the stored bag and gets the game`() {
    givenOwned()
    givenIdleTheodolite()
    givenStoredLevel(1)
    every { inventoryService.consumeAll(MASTER_ID, listOf(PAPER to 1, INK to 1)) } returns true

    val game = assertIs<ZoneAnswer.SurveyGame>(start().answer)

    assertEquals(7, game.structureId)
    assertEquals(SurveyMinigame.NEEDLES, game.arcCentresDegrees.size)
  }

  @Test
  fun `a master in the world pays from what it carries and is judged by its live level`() {
    givenOwned()
    givenIdleTheodolite()
    givenStoredLevel(0)
    every { inventoryService.consumeAll(MASTER_ID, any(), null) } returns true
    givenLiveMaster(cartographyLevel = 3, Inventory.Item(PAPER, 2), Inventory.Item(INK, 1))

    assertIs<ZoneAnswer.SurveyGame>(start(km = 3).answer)

    assertEquals(mapOf(PAPER to 1), carried())
    verify { inventoryService.consumeAll(MASTER_ID, listOf(PAPER to 1, INK to 1), null) }
  }

  @Test
  fun `a master in the world without ink keeps its paper`() {
    givenOwned()
    givenIdleTheodolite()
    givenStoredLevel(1)
    givenLiveMaster(cartographyLevel = 1, Inventory.Item(PAPER, 2))

    assertRefused(OpError.SKILL_MISSING_REAGENT, start())
    assertEquals(mapOf(PAPER to 2), carried())
  }

  @Test
  fun `stops inside every arc start the work, others miss`() {
    givenOwned()
    givenIdleTheodolite()
    givenStoredLevel(1)
    every { inventoryService.consumeAll(MASTER_ID, any()) } returns true

    val won = stops(winningStops(assertIs<ZoneAnswer.SurveyGame>(start().answer)))
    assertEquals(ZoneAnswer.SurveyOutcome("WON"), won.answer)
    verify { structures.setSurvey(7, SurveyTier.KM_1.radiusMetres, TheodoliteSurveys.WORK_SECONDS) }

    val game = assertIs<ZoneAnswer.SurveyGame>(start().answer)
    val missed = stops(winningStops(game).map { it + game.swingPeriodMs / 2 })
    assertEquals(ZoneAnswer.SurveyOutcome("MISSED"), missed.answer)
  }

  @Test
  fun `stops without an open game are not found`() {
    givenOwned()
    givenIdleTheodolite()

    assertEquals(Status.NOT_FOUND, stops(listOf(1, 2, 3)).status)
  }

  private fun start(km: Int = 1): BusReply {
    return commands.start("c", ACCOUNT_ID, ZoneQuery.StartSurvey(MASTER_ID, structureId = 7, km = km))
  }

  private fun stops(stopsMs: List<Long>): BusReply {
    return commands.stops("c", ACCOUNT_ID, ZoneQuery.SurveyStops(MASTER_ID, structureId = 7, stopsMs = stopsMs))
  }

  private fun assertRefused(error: OpError, reply: BusReply) {
    assertEquals(Status.INVALID, reply.status)
    assertEquals(error.name, reply.detail)
  }

  private fun givenOwned() {
    every { owned.find(ACCOUNT_ID, MASTER_ID) } returns master
  }

  private fun givenIdleTheodolite() {
    every { structures.of(7) } returns idle
  }

  private fun givenStoredLevel(stored: Int) {
    every { learnedSkills.findByMasterIdAndSkillId(MASTER_ID, CARTOGRAPHY_ID) } returns mockk<LearnedSkill> {
      every { level } returns stored
    }
  }

  private fun givenLiveMaster(cartographyLevel: Int, vararg carried: Inventory.Item) {
    world.createEntity(MASTER_ENTITY) { id ->
      add(id, MasterComponent(MASTER_ID))
      add(id, KnownSkills(mutableMapOf(CARTOGRAPHY_ID to cartographyLevel)))
      add(id, Inventory(carried.toMutableList()))
    }
  }

  /** Item id -> amount the live master carries. */
  private fun carried(): Map<Long, Int> {
    return world.read {
      get(MASTER_ENTITY, Inventory::class)!!.getItems().filter { it.amount > 0 }.associate { it.itemId to it.amount }
    }
  }

  private fun theodolite(
    id: Long,
    ownerMasterId: Long = MASTER_ID,
    kind: StaticEntityKind = StaticEntityKind.THEODOLITE,
    totalBuildSeconds: Float = 0f,
    surveyRadiusMetres: Int? = null,
    surveyRemainingSeconds: Float = 0f,
  ): StructureEntry {
    return StructureEntry(
      id = id, kind = kind, ownerMasterId = ownerMasterId, ownerAccountId = ACCOUNT_ID, position = Vec3L(10, 20, 3),
      yaw = 0f, totalBuildSeconds = totalBuildSeconds, remainingBuildSeconds = totalBuildSeconds,
      surveyRadiusMetres = surveyRadiusMetres, surveyRemainingSeconds = surveyRemainingSeconds,
    )
  }

  /** For each needle, the first millisecond of its round at which it points at its arc's centre. */
  private fun winningStops(game: ZoneAnswer.SurveyGame): List<Long> {
    val minigame = SurveyMinigame(game.swingPeriodMs, game.arcWidthDegrees, game.arcCentresDegrees)
    return game.arcCentresDegrees.map { centre ->
      (0L until game.swingPeriodMs).minBy { abs(minigame.needleAt(it) - centre) }
    }
  }

  private companion object {
    const val ACCOUNT_ID = 1L
    const val MASTER_ID = 42L
    const val MASTER_ENTITY = 900L
    const val CARTOGRAPHY_ID = 37L
    const val PAPER = 501L
    const val INK = 502L
  }
}
