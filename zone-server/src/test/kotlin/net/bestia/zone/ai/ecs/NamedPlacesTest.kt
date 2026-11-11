package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.NamedPlace
import net.bestia.zone.ai.domain.bestia.WorkArea
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A bestia whose orders name a work area or a chest on the map, instead of its home and the nearest chest. */
class NamedPlacesTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(0, 0, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  private fun area(id: Long, center: Vec3L, radius: Long) {
    ai.places[id] = NamedPlace(id, MASTER, "Field $id", NamedPlace.Kind.AREA, center, radius, null)
  }

  private fun gatherer(orders: AiConfig): EntityId {
    val id = ai.spawnMob("passive_wanderer", home, orders = orders)
    ai.world.add(id, OwnedBestia(masterId = MASTER, playerBestiaId = 1L))
    return id
  }

  @Test
  fun `a gatherer works the named area and walks there`() {
    area(1L, Vec3L(40, 0, 0), radius = 6)
    val gatherer = gatherer(AiConfig(BestiaJob.GATHER, areaPlaceId = 1L))

    ai.tickUntil(describe = { "it stands at ${ai.positionOf(gatherer)}" }) {
      ai.positionOf(gatherer).distance(Vec3L(40, 0, 0)) <= 6
    }
    assertEquals(WorkArea(Vec3L(40, 0, 0), 6), ai.beliefOf(gatherer, BestiaDomain.GATHER_AREA))
  }

  @Test
  fun `moving the place moves the bestia's area`() {
    area(1L, Vec3L(10, 0, 0), radius = 8)
    val gatherer = gatherer(AiConfig(BestiaJob.GATHER, areaPlaceId = 1L))
    ai.tickUntil { ai.beliefOf(gatherer, BestiaDomain.GATHER_AREA)?.center == Vec3L(10, 0, 0) }

    area(1L, Vec3L(-20, 0, 0), radius = 12)

    ai.tickUntil { ai.beliefOf(gatherer, BestiaDomain.GATHER_AREA) == WorkArea(Vec3L(-20, 0, 0), 12) }
  }

  @Test
  fun `a named chest is the job chest, though another is nearer`() {
    ai.spawnChest(Vec3L(3, 0, 0), structureId = 8L, masterId = MASTER)
    ai.spawnChest(Vec3L(30, 0, 0), structureId = 9L, masterId = MASTER)
    ai.places[2L] = NamedPlace(2L, MASTER, "Far chest", NamedPlace.Kind.CHEST, Vec3L(30, 0, 0), 0, chestStructureId = 9L)
    val gatherer = gatherer(AiConfig(BestiaJob.GATHER, chestPlaceId = 2L))

    ai.tickUntil { ai.beliefOf(gatherer, BestiaDomain.JOB_CHEST) != null }

    assertEquals(9L, ai.beliefOf(gatherer, BestiaDomain.JOB_CHEST)?.structureId)
  }

  private companion object {
    const val MASTER = 7L
  }
}
