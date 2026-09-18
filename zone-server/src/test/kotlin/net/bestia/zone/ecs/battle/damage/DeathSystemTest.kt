package net.bestia.zone.ecs.battle.damage

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.exp.ExperienceGainCalculator
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.entity.VisualKind
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ai.rumour.NotableKillReporter
import net.bestia.zone.ecs.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.ecs.persistence.Persistent
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.item.loot.LootItemEntitySpawner
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Who is gone for good when they die, and who is not.
 *
 * The ownership gate carries two rules at once: a player's body stays in the world to be respawned,
 * and killing somebody's pet pays out nothing - an owned bestia is `EntityVisual(BESTIA)` exactly
 * like the wild version, which is all the species lookup behind EXP and loot ever looked at.
 */
class DeathSystemTest {

  private val lootSpawner = mockk<LootItemEntitySpawner>(relaxed = true)
  private val deletionQueue = PersistedEntityDeletionQueue()

  /** Where blood was spilled, so the once-per-death rule can be asserted rather than assumed. */
  private val spilledAt = mutableListOf<Pair<Long, Long>>()

  private val sut = DeathSystem(
    experienceGainCalculator = mockk<ExperienceGainCalculator>(relaxed = true),
    lootItemEntitySpawner = lootSpawner,
    deletionQueue = deletionQueue,
    connectionInfoService = ConnectionInfoService(),
    notableKills = mockk<NotableKillReporter>(relaxed = true),
    spill = { x, y -> spilledAt += x to y },
  )

  private fun net.bestia.zone.ecs.core.World.deadBestia(
    owner: Long? = null,
    persistent: Boolean = true
  ) = createEntity { eid ->
    add(eid, Position.fromVec3(Vec3L(1, 2, 3)))
    add(eid, EntityVisual(VisualKind.BESTIA, 7L))
    add(eid, Dead())
    // Every mob `BestiaEntitySpawner` persists carries this, so a fixture without it was modelling a
    // creature that cannot exist - and the deletion assertion below is about exactly that marker.
    if (persistent) add(eid, Persistent)
    if (owner != null) add(eid, Account(owner))
  }

  @Test
  fun `a wild mob is destroyed and its row queued for deletion`() {
    val world = testWorld()
    val id = world.deadBestia(owner = null)

    sut.update(world, 0f)

    assertFalse(world.isAlive(id))
    assertEquals(listOf(id), deletionQueue.drainAll())
  }

  @Test
  fun `a transient mob is destroyed without queueing a deletion`() {
    val world = testWorld()
    val id = world.deadBestia(persistent = false)

    sut.update(world, 0f)

    assertFalse(world.isAlive(id))
    assertEquals(emptyList<EntityId>(), deletionQueue.drainAll())
  }

  @Test
  fun `a wild mob drops loot`() {
    val world = testWorld()
    world.deadBestia(owner = null)

    sut.update(world, 0f)

    verify { lootSpawner.spawnLoot(any(), 7L, Vec3L(1, 2, 3)) }
  }

  @Test
  fun `a player-owned body stays in the world`() {
    val world = testWorld()
    val id = world.deadBestia(owner = 42L)

    sut.update(world, 0f)

    assertTrue(world.isAlive(id))
    assertTrue(world.has(id, Dead::class))
    assertTrue(deletionQueue.drainAll().isEmpty())
  }

  @Test
  fun `killing somebody's bestia drops no loot`() {
    val world = testWorld()
    world.deadBestia(owner = 42L)

    sut.update(world, 0f)

    verify(exactly = 0) { lootSpawner.spawnLoot(any(), any(), any()) }
  }

  @Test
  fun `a death leaves blood where it fell`() {
    val world = testWorld()
    world.deadBestia(owner = null)

    sut.update(world, 0f)

    assertEquals(listOf(1L to 2L), spilledAt)
  }

  /** Ahead of the early return for an owned body, because a player bleeds too. */
  @Test
  fun `a player-owned body bleeds as well`() {
    val world = testWorld()
    world.deadBestia(owner = 42L)

    sut.update(world, 0f)

    assertEquals(listOf(1L to 2L), spilledAt)
  }

  /**
   * **The reason `Dead.bled` exists.** A body lies where it fell for as long as its owner leaves it, and this
   * system runs over it every tick - so without the flag the ground under a corpse would soak deeper for ever.
   */
  @Test
  fun `a body lying there does not bleed again every tick`() {
    val world = testWorld()
    world.deadBestia(owner = 42L)

    repeat(5) { sut.update(world, 0f) }

    assertEquals(1, spilledAt.size)
  }
}
