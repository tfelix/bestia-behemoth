package net.bestia.zone.prop

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.GrowthStage
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.prop.persistence.DivergenceState
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** What each way of removing a prop leaves behind. */
class PropRemovalTest {

  private val now = Instant.parse("2026-11-06T12:00:00Z")
  private val divergence: WorldObjectDivergenceRegistry = mockk(relaxed = true)
  private val residency: WorldObjectResidencyService = mockk(relaxed = true) {
    every { replace(any(), any(), any()) } returns 99L
  }
  private val removal = PropRemoval(
    PropKindRegistry().also { it.load() }, divergence, residency, Clock.fixed(now, ZoneOffset.UTC)
  )

  private fun World.prop(kind: StaticEntityKind, propId: Long = 5L) = createEntity { id ->
    add(id, StaticVisual(kind, variant = 0, heightDm = 60))
    add(id, WorldObjectIdentity(propId, latticeVersion = 1L))
  }

  @Test
  fun `a tree used up in any way leaves a stump`() {
    val world = testWorld()
    val tree = world.prop(StaticEntityKind.TREE)

    removal.removed(world, tree, PropRemoval.Cause.USED_UP)

    verify { divergence.record(5L, StaticEntityKind.TREE, DivergenceState.STUMP, now.plus(Duration.ofDays(7))) }
    verify { residency.replace(world, tree, GrowthStage.STUMP) }
  }

  @Test
  fun `a blighted tree leaves a stump too`() {
    val world = testWorld()
    val tree = world.prop(StaticEntityKind.BLIGHTED_TREE)

    removal.removed(world, tree, PropRemoval.Cause.USED_UP)

    verify { residency.replace(world, tree, GrowthStage.STUMP) }
  }

  @Test
  fun `a tree gone for good leaves nothing and never comes back`() {
    val world = testWorld()
    val tree = world.prop(StaticEntityKind.TREE)

    removal.removed(world, tree, PropRemoval.Cause.GONE_FOR_GOOD)

    verify { divergence.recordDepletion(5L, StaticEntityKind.TREE, null) }
    verify { residency.remove(world, tree) }
    verify(exactly = 0) { residency.replace(any(), any(), any()) }
  }

  @Test
  fun `a picked herb comes back on its own timer, as before`() {
    val world = testWorld()
    val herb = world.prop(StaticEntityKind.HERB)

    removal.removed(world, herb, PropRemoval.Cause.USED_UP)

    verify { divergence.recordDepletion(5L, StaticEntityKind.HERB, now.plusSeconds(600)) }
    verify { residency.remove(world, herb) }
  }

  @Test
  fun `a tree in a column nobody holds still leaves a stump for when it loads`() {
    removal.removedUnseen(5L, StaticEntityKind.TREE, PropRemoval.Cause.USED_UP)

    verify { divergence.record(5L, StaticEntityKind.TREE, DivergenceState.STUMP, any()) }
  }

  @Test
  fun `a player's structure is left to its own system`() {
    val world = testWorld()
    val structure = world.prop(StaticEntityKind.TREE, propId = 0L)

    removal.removed(world, structure, PropRemoval.Cause.USED_UP)

    verify(exactly = 0) { divergence.record(any(), any(), any(), any()) }
    verify(exactly = 0) { residency.remove(any(), any()) }
  }
}
