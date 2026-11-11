package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.station.QueueEntry
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A bestia whose main job ran out of work doing its fallback job, and going back as soon as there is work again. */
class FallbackJobTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(0, 0, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  private fun worker(job: BestiaJob, fallback: BestiaJob?): EntityId {
    val id = ai.spawnMob("passive_wanderer", home, orders = AiConfig(job, fallbackJob = fallback))
    ai.world.add(id, OwnedBestia(masterId = MASTER, playerBestiaId = 1L))
    return id
  }

  private fun station(structureId: Long, at: Vec3L, hasWork: Boolean = true) {
    val queue = if (hasWork) listOf(QueueEntry(recipeId = 1L, amount = 2)) else emptyList()
    ai.structures.add(
      StructureEntry(
        structureId, StaticEntityKind.WORKBENCH, MASTER, ownerAccountId = null, at, yaw = 0f, queue = queue,
        takesAnyCrafter = true,
      )
    )
  }

  private fun shaft(structureId: Long, at: Vec3L) {
    ai.structures.add(StructureEntry(structureId, StaticEntityKind.MINING_SHAFT, MASTER, ownerAccountId = null, at, yaw = 0f))
  }



  private fun workplaceOf(id: EntityId): Long? = ai.world.get(id, WorkAssignment::class)?.structureId

  @Test
  fun `a gatherer with nothing to gather crafts`() {
    val gatherer = worker(BestiaJob.GATHER, fallback = BestiaJob.CRAFT)
    station(1L, Vec3L(6, 0, 0))

    ai.tickUntil(describe = { "it works at ${workplaceOf(gatherer)}" }) { workplaceOf(gatherer) == 1L }
  }

  @Test
  fun `a crafter mining for want of work goes back to crafting once a station has some`() {
    val crafter = worker(BestiaJob.CRAFT, fallback = BestiaJob.MINE)
    shaft(1L, Vec3L(6, 0, 0))
    ai.tickUntil { workplaceOf(crafter) == 1L }

    station(2L, Vec3L(-6, 0, 0))

    ai.tickUntil(describe = { "it works at ${workplaceOf(crafter)}" }) { workplaceOf(crafter) == 2L }
  }

  @Test
  fun `a miner never takes a station while a shaft waits`() {
    val miner = worker(BestiaJob.MINE, fallback = BestiaJob.CRAFT)
    station(1L, Vec3L(3, 0, 0))
    shaft(2L, Vec3L(12, 0, 0))

    ai.tickUntil { workplaceOf(miner) != null }

    assertEquals(2L, workplaceOf(miner))
  }

  @Test
  fun `a crafter that guards for want of work leaves a stranger alone while a station has work`() {
    val crafter = ai.spawnMob("aggressive_melee", home, orders = AiConfig(BestiaJob.CRAFT, fallbackJob = BestiaJob.GUARD))
    ai.world.add(crafter, OwnedBestia(masterId = MASTER, playerBestiaId = 1L))
    ai.world.add(crafter, Account(MASTER * 10))
    ai.world.add(crafter, Level(10))
    station(1L, Vec3L(4, 0, 0))
    ai.spawnPlayer(Vec3L(6, 0, 0))

    repeat(20 * 10) {
      ai.tick()
      assertNotEquals("KillEnemy", ai.goalNameOf(crafter), "it left its station to guard")
    }

    // Once the queue is done the station lets it go, and guarding is what it does.
    ai.stationWorkers.unassign(ai.world, 1L)
    ai.structures.replaceAll { if (it.id == 1L) it.copy(queue = emptyList()) else it }

    ai.tickUntilGoal(crafter, "KillEnemy")
  }

  @Test
  fun `without a fallback a bestia whose job has nothing to do takes no other work`() {
    val crafter = worker(BestiaJob.CRAFT, fallback = null)
    shaft(1L, Vec3L(6, 0, 0))

    repeat(20 * 20) {
      ai.tick()
      assertNotEquals(1L, workplaceOf(crafter))
    }
  }

  @Test
  fun `only a job that runs out of work takes a fallback, and never itself or rest`() {
    assertTrue(AiConfig(BestiaJob.GATHER, fallbackJob = BestiaJob.GUARD).isValid)
    assertFalse(AiConfig(BestiaJob.GUARD, fallbackJob = BestiaJob.GATHER).isValid)
    assertFalse(AiConfig(BestiaJob.REST, fallbackJob = BestiaJob.CRAFT).isValid)
    assertFalse(AiConfig(BestiaJob.MINE, fallbackJob = BestiaJob.MINE).isValid)
    assertFalse(AiConfig(BestiaJob.CRAFT, fallbackJob = BestiaJob.REST).isValid)
  }

  private companion object {
    /** Not 1: the fixture's players are master 1, and a bestia never guards against its own master. */
    const val MASTER = 7L
  }
}
