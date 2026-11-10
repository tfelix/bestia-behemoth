package net.bestia.zone.prop

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.battle.ecs.exp.GainExp
import net.bestia.zone.item.GatheringExp
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.stat.ItemStatCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.loot.LootItemEntitySpawner
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import net.bestia.zone.entity.GrowthStage
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.persistence.DivergenceState

/** What a promoted prop's death records about the durable object it was, and what it drops. */
class PropDeathDivergenceSystemTest {

  private val now = Instant.parse("2026-11-06T12:00:00Z")
  private val kinds = PropKindRegistry().also { it.load() }
  private val residency: WorldObjectResidencyService = mockk(relaxed = true)
  private val loot: LootItemEntitySpawner = mockk(relaxed = true)

  private fun system(divergence: WorldObjectDivergenceRegistry) = PropDeathDivergenceSystem(
    kinds, loot, divergence, PropRemoval(kinds, divergence, residency, Clock.fixed(now, ZoneOffset.UTC)),
    GatheringExp(ItemStatCatalog()),
  )

  /**
   * A prop nothing has claimed yet - the state every test but the collect one below starts from.
   *
   * Stated rather than left to `relaxed = true`, which answers a nullable getter with a stub object rather
   * than with null. That would trip the "already depleted" guard and make these tests pass or fail for a
   * reason that has nothing to do with what they are checking.
   */
  private fun pristine(): WorldObjectDivergenceRegistry = mockk(relaxed = true) {
    every { of(any()) } returns null
  }

  private fun growing(stage: DivergenceState): WorldObjectDivergenceRegistry = mockk(relaxed = true) {
    every { of(any()) } returns DivergenceEntry(StaticEntityKind.TREE, stage, now)
  }

  private fun net.bestia.zone.ecs.core.World.deadTree(propId: Long, growth: GrowthStage = GrowthStage.GROWN) =
    createEntity { eid ->
      add(eid, Position.fromVec3(Vec3L(1, 2, 3)))
      add(eid, StaticVisual(StaticEntityKind.TREE, variant = 0, heightDm = 90, growth = growth))
      add(eid, WorldObjectIdentity(propId, latticeVersion = 1L))
      add(eid, Dead())
    }

  @Test
  fun `a felled tree leaves a stump that tries to sprout a week later`() {
    val divergence = pristine()
    val world = testWorld()
    val tree = world.deadTree(propId = 123L)

    system(divergence).update(world, 0f)

    verify {
      divergence.record(123L, StaticEntityKind.TREE, DivergenceState.STUMP, now.plus(Duration.ofDays(7)))
    }
    verify { residency.replace(world, tree, GrowthStage.STUMP) }
  }

  @Test
  fun `a grown tree drops four logs`() {
    val world = testWorld()
    world.deadTree(propId = 123L)

    system(pristine()).update(world, 0f)

    verify { loot.spawnLootItem(world, itemId = LOG, amount = 4, pos = Vec3L(1, 2, 3), lootOwner = any()) }
  }

  @Test
  fun `the bestia that felled a tree earns experience for its logs and resin`() {
    val world = testWorld()
    val bestia = world.createEntity { eid -> add(eid, OwnedBestia(masterId = 1L, playerBestiaId = 9L)) }
    val helper = world.createEntity { }
    val tree = world.deadTree(propId = 123L)
    world.add(tree, TakenDamage().apply {
      addDamage(bestia, 100)
      addDamage(helper, 20)
    })

    system(pristine()).update(world, 0f)

    // Four logs and two resin at item level 1: 2 * 1 + 5 each.
    assertEquals(42, world.get(bestia, GainExp::class)?.value)
  }

  @Test
  fun `a young tree can be felled again, for half the logs`() {
    val divergence = growing(DivergenceState.YOUNG)
    val world = testWorld()
    world.deadTree(propId = 123L, growth = GrowthStage.YOUNG)

    system(divergence).update(world, 0f)

    verify { loot.spawnLootItem(world, itemId = LOG, amount = 2, pos = any(), lootOwner = any()) }
    verify { divergence.record(123L, StaticEntityKind.TREE, DivergenceState.STUMP, any()) }
  }

  @Test
  fun `a felled sapling goes back to a stump and drops nothing`() {
    val divergence = growing(DivergenceState.SAPLING)
    val world = testWorld()
    world.deadTree(propId = 123L, growth = GrowthStage.SAPLING)

    system(divergence).update(world, 0f)

    verify(exactly = 0) { loot.spawnLootItem(any(), any(), any(), any(), lootOwner = any()) }
    verify { divergence.record(123L, StaticEntityKind.TREE, DivergenceState.STUMP, any()) }
  }

  @Test
  fun `a claimed landmark is recorded as terminal and taken off the column`() {
    val divergence = pristine()
    val world = testWorld()
    val propId = 456L

    val landmark = world.createEntity { eid ->
      add(eid, Position.fromVec3(Vec3L(1, 2, 3)))
      add(eid, StaticVisual(StaticEntityKind.POI_LOST_GRAVE, variant = 0, heightDm = 12))
      add(eid, WorldObjectIdentity(propId, latticeVersion = 1L))
      add(eid, Dead())
    }

    system(divergence).update(world, 0f)

    verify { divergence.recordDepletion(propId, StaticEntityKind.POI_LOST_GRAVE, null) }
    verify { residency.remove(world, landmark) }
  }

  /**
   * A prop now has two ways to be used up, and they must not both pay out. If a crystal is collected at order
   * 64 and an attacker's in-flight damage finishes it at order 65 in the same tick, only the collect counts.
   */
  @Test
  fun `a prop already depleted by a collect is not paid out again on death`() {
    val divergence: WorldObjectDivergenceRegistry = mockk(relaxed = true) {
      every { of(any()) } returns
        DivergenceEntry(StaticEntityKind.MANA_CRYSTAL_SMALL, DivergenceState.DEPLETED, null)
    }
    val world = testWorld()

    world.createEntity { eid ->
      add(eid, Position.fromVec3(Vec3L(1, 2, 3)))
      add(eid, StaticVisual(StaticEntityKind.MANA_CRYSTAL_SMALL, variant = 0, heightDm = 12))
      add(eid, WorldObjectIdentity(789L, latticeVersion = 1L))
      add(eid, Dead())
    }

    system(divergence).update(world, 0f)

    verify(exactly = 0) { divergence.record(any(), any(), any(), any()) }
    verify(exactly = 0) { divergence.recordDepletion(any(), any(), any()) }
  }

  @Test
  fun `a dead entity with no WorldObjectIdentity is ignored`() {
    val divergence = pristine()
    val world = testWorld()

    world.createEntity { eid -> add(eid, Dead()) }

    system(divergence).update(world, 0f)

    verify(exactly = 0) { divergence.recordDepletion(any(), any(), any()) }
  }

  private companion object {
    /** `log` in `items.yml`. */
    const val LOG = 43L
  }
}
