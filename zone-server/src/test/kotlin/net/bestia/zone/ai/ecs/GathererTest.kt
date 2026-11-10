package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.battle.ecs.exp.GainExp
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A bestia under GATHER orders working the area around its home with nobody driving it. */
class GathererTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(0, 0, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  private fun bestia(job: BestiaJob = BestiaJob.GATHER): EntityId {
    val id = ai.spawnMob("passive_wanderer", home, orders = AiConfig(job))
    ai.world.add(id, OwnedBestia(masterId = 1L, playerBestiaId = id))
    return id
  }

  private fun isTaken(propId: Long): Boolean = ai.divergence.of(propId) != null

  @Test
  fun `a gatherer collects what lies in its area and earns experience for it`() {
    val gatherer = bestia()
    ai.spawnProp(StaticEntityKind.STICK, Vec3L(5, 0, 0), propId = 1L)
    ai.spawnProp(StaticEntityKind.LOOSE_STONE, Vec3L(-6, 4, 0), propId = 2L)
    // The wanderer's area is 32 around home.
    ai.spawnProp(StaticEntityKind.STICK, Vec3L(50, 0, 0), propId = 3L)

    ai.tickUntil(describe = { "it took ${listOf(1L, 2L).filter(::isTaken)}" }) { isTaken(1L) && isTaken(2L) }
    ai.tick(times = 20 * 10)

    assertNull(ai.divergence.of(3L), "it gathered outside its area")
    // A stick and a stone at item level 1: 2 * 1 + 5 each.
    assertEquals(14, ai.world.getOrThrow(gatherer, GainExp::class).value)
  }

  @Test
  fun `a gatherer whose bag is nearly full gathers no more`() {
    val gatherer = bestia()
    ai.world.add(gatherer, Inventory(mutableListOf(Inventory.Item(itemId = 43, amount = 8, weight = 1))))
    ai.world.add(gatherer, CarryCapacity(8, 10))
    ai.spawnProp(StaticEntityKind.STICK, Vec3L(5, 0, 0), propId = 1L)

    ai.tick(times = 20 * 20)

    assertNull(ai.divergence.of(1L))
  }

  @Test
  fun `a bestia told to rest leaves the sticks alone`() {
    bestia(BestiaJob.REST)
    ai.spawnProp(StaticEntityKind.STICK, Vec3L(5, 0, 0), propId = 1L)

    ai.tick(times = 20 * 20)

    assertNull(ai.divergence.of(1L))
  }
}
