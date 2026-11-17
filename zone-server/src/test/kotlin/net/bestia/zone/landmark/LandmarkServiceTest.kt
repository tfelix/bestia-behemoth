package net.bestia.zone.landmark

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.poi.PoiKind
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.Landmark
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.landmark.persistence.DestroyedLandmark
import net.bestia.zone.landmark.persistence.DestroyedLandmarkRepository
import net.bestia.zone.landmark.script.ExamineLandmarkScript
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PropKindRegistry
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LandmarkServiceTest {

  private val kinds = PropKindRegistry().also { it.load() }
  private val statusEffects = mockk<StatusEffectService>()
  private val repository = mockk<DestroyedLandmarkRepository>(relaxed = true)
  private val world = testWorld()

  private val worldService = mockk<WorldService>().also {
    every { it.generated } returns generated
    every { it.record.id } returns WORLD_ID
  }

  private val service = LandmarkService(
    worldService = worldService,
    kinds = kinds,
    scripts = LandmarkScriptRegistry(listOf(ExamineLandmarkScript(mockk())), kinds),
    statusEffectService = statusEffects,
    repository = repository,
    // Runs the durable write on the calling thread, so a test can assert on it without waiting.
    asyncJobExecutor = mockk<AsyncJobExecutor>().also {
      every { it.submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
    },
  )

  init {
    every {
      statusEffects.applyEffect(any(), any(), any<StatusEffectId>(), any(), any(), any(), any(), any())
    } returns true
  }

  @Test
  fun `every landmark of the world stands as an entity that can be fought`() {
    every { repository.findAll() } returns emptyList()

    service.raise(world)

    val raised = landmarksIn(world)
    assertEquals(LandmarkService.sitesOf(generated).map { it.kind to it.copy }.toSet(), raised.keys)
    assertTrue(PoiKind.BROKEN_OBELISK to 0 in raised.keys, "the required obelisk is missing")

    val obelisk = raised.getValue(PoiKind.BROKEN_OBELISK to 0)
    val visual = assertNotNull(world.get(obelisk, EntityVisual::class))
    assertEquals(VisualKind.STRUCTURE, visual.kind)
    assertEquals(StaticEntityKind.POI_BROKEN_OBELISK.ordinal.toLong(), visual.id)
    assertEquals(kinds.of(StaticEntityKind.POI_BROKEN_OBELISK).maxHp, world.get(obelisk, Health::class)?.max)
    assertNotNull(world.get(obelisk, CombatStats::class))
  }

  @Test
  fun `a configured effect is put on for good`() {
    every { repository.findAll() } returns emptyList()

    service.raise(world)

    val obelisk = landmarksIn(world).getValue(PoiKind.BROKEN_OBELISK to 0)
    verify {
      statusEffects.applyEffect(
        any(), obelisk, StatusEffectId.INVULNERABLE, 1, null, Double.POSITIVE_INFINITY, any(), any()
      )
    }
  }

  @Test
  fun `a destroyed landmark is not raised again, and a row of another world is dropped`() {
    val destroyedObelisk = row(PoiKind.BROKEN_OBELISK, WORLD_ID, generated.world.pipelineVersion)
    val stale = row(PoiKind.LOST_GRAVE, WORLD_ID + 1, generated.world.pipelineVersion)
    every { repository.findAll() } returns listOf(destroyedObelisk, stale)

    service.raise(world)

    assertTrue(PoiKind.BROKEN_OBELISK to 0 !in landmarksIn(world).keys)
    verify { repository.deleteAll(listOf(stale)) }
  }

  @Test
  fun `destroying a landmark stores it`() {
    every { repository.findAll() } returns emptyList()
    service.raise(world)
    val obelisk = landmarksIn(world).getValue(PoiKind.BROKEN_OBELISK to 0)
    val saved = slot<DestroyedLandmark>()
    every { repository.save(capture(saved)) } answers { saved.captured }

    service.destroyed(world, obelisk)

    assertEquals(DestroyedLandmark.Key(PoiKind.BROKEN_OBELISK.name, 0), saved.captured.id)
    assertEquals(WORLD_ID, saved.captured.worldId)
  }

  private fun landmarksIn(world: EcsWorld): Map<Pair<PoiKind, Int>, EntityId> {
    val found = HashMap<Pair<PoiKind, Int>, EntityId>()
    world.query(Landmark::class).each { id ->
      val landmark = get<Landmark>()
      found[landmark.kind to landmark.copy] = id
    }
    return found
  }

  private fun row(kind: PoiKind, worldId: Long, pipelineVersion: Long): DestroyedLandmark {
    return DestroyedLandmark(
      id = DestroyedLandmark.Key(kind.name, 0),
      worldId = worldId,
      pipelineVersion = pipelineVersion
    )
  }

  private companion object {
    const val WORLD_ID = 7L

    val generated: GeneratedWorld
      get() = TestWorlds.get(WorldConfig(seed = 9001L, widthCells = 48, heightCells = 48))
  }
}
