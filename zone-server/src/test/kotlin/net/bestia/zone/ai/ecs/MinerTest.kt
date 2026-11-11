package net.bestia.zone.ai.ecs

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.crafting.Crafters
import net.bestia.zone.crafting.StarterKit
import net.bestia.zone.crafting.station.CrafterProfiles
import net.bestia.zone.crafting.station.ShaftWorkSystem
import net.bestia.zone.crafting.station.StationWorkers
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.mining.MiningSkill
import net.bestia.zone.item.mining.ShaftOre
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A bestia digging the ore below its master's mining shaft. */
class MinerTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(0, 0, 0)
  private val shaftAt = Vec3L(6, 0, 0)

  /** What is left below the shaft, in ore voxels. */
  private var oreLeft = 3

  private val ore = mockk<ShaftOre> {
    every { hasOre(any(), any(), any()) } answers { oreLeft > 0 }
    every { keepOnly(any()) } returns Unit
    every { dig(any(), any(), any(), any(), any()) } answers {
      oreLeft--
      ai.world.getOrThrow(arg<EntityId>(4), Inventory::class).addItem(Inventory.Item(ORE, 2, weight = ORE_WEIGHT))
      1
    }
  }

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
    ai.world.registerSystems(listOf(shaftWork()))
  }

  private fun miner(job: BestiaJob = BestiaJob.MINE): EntityId {
    val id = ai.spawnMob("passive_wanderer", home, orders = AiConfig(job))
    ai.world.add(id, OwnedBestia(masterId = MASTER, playerBestiaId = 1L))
    ai.world.add(id, Inventory(mutableListOf()))
    ai.world.add(id, CarryCapacity(0, 10_000))
    return id
  }

  private fun shaft(structureId: Long = 1L, at: Vec3L = shaftAt): StructureEntry {
    val entry = StructureEntry(structureId, StaticEntityKind.MINING_SHAFT, MASTER, ownerAccountId = null, at, yaw = 0f)
    ai.structures.add(entry)
    return entry
  }

  private fun oreIn(id: EntityId): Int {
    return ai.world.getOrThrow(id, Inventory::class).getItems().filter { it.itemId == ORE }.sumOf { it.amount }
  }

  private fun workplaceOf(id: EntityId): Long? = ai.world.get(id, WorkAssignment::class)?.structureId

  @Test
  fun `a bestia set to a shaft digs ore into its bag`() {
    val miner = miner()
    ai.stationWorkers.assign(ai.world, miner, shaft())

    ai.tickUntil(maxTicks = 20 * 120, describe = { "it holds ${oreIn(miner)} ore at ${ai.positionOf(miner)}" }) {
      oreIn(miner) >= 2
    }
  }

  @Test
  fun `a shaft with nothing left in reach lets its miner go`() {
    oreLeft = 0
    val miner = miner()
    ai.stationWorkers.assign(ai.world, miner, shaft())

    ai.tick(times = 20)

    assertNull(workplaceOf(miner))
  }

  @Test
  fun `a miner with a full bag is asked to empty it before the next carve`() {
    val miner = miner()
    ai.world.add(miner, CarryCapacity(0, ShaftWorkSystem.ROOM_TO_DIG - 1))
    ai.stationWorkers.assign(ai.world, miner, shaft())

    ai.tickUntil(maxTicks = 20 * 30) { ai.beliefOf(miner, BestiaDomain.NO_ROOM_FOR_PRODUCT) == true }

    verify(exactly = 0) { ore.dig(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a miner takes the nearest shaft nobody works and goes to work there`() {
    val miner = miner()
    shaft(1L, Vec3L(12, 0, 0))
    val near = shaft(2L, Vec3L(-6, 0, 0))

    ai.tickUntil(describe = { "it works at ${workplaceOf(miner)}, standing at ${ai.positionOf(miner)}" }) {
      workplaceOf(miner) == 2L && ai.positionOf(miner).distance(near.position) <= StationWorkers.WORK_RADIUS
    }
  }

  @Test
  fun `a miner leaves alone shafts it cannot work`() {
    val miner = miner()
    every { ai.shaftOre.isDugOut(1L) } returns true
    shaft(1L, Vec3L(6, 0, 0))
    ai.structures.add(
      StructureEntry(2L, StaticEntityKind.MINING_SHAFT, 99L, ownerAccountId = null, Vec3L(-6, 0, 0), yaw = 0f)
    )
    ai.structures.add(
      StructureEntry(3L, StaticEntityKind.MINING_SHAFT, MASTER, null, Vec3L(0, 6, 0), yaw = 0f, workerBestiaId = 7L)
    )
    // The wanderer's area is 32 around home.
    shaft(4L, Vec3L(50, 0, 0))

    ai.tick(times = 20 * 20)

    assertNull(workplaceOf(miner))
  }

  @Test
  fun `a bestia with another job takes no shaft`() {
    val crafter = miner(BestiaJob.CRAFT)
    shaft()

    ai.tick(times = 20 * 20)

    assertNull(workplaceOf(crafter))
  }

  @Test
  fun `a miner carries its ore to the chest and goes back to dig`() {
    val miner = miner()
    // A carve brings 600 of ore, after which the next one does not fit.
    ai.world.add(miner, CarryCapacity(0, 1_000))
    shaft()
    val chest = ai.spawnChest(Vec3L(10, 0, 0), structureId = 9L, masterId = MASTER)

    ai.tickUntil(maxTicks = 20 * 300, describe = { "the chest holds ${oreIn(chest)}" }) { oreIn(chest) >= 4 }
  }

  @Test
  fun `a miner let go by a dug out shaft goes home`() {
    val miner = miner()
    shaft()
    ai.tickUntil { workplaceOf(miner) == 1L }

    oreLeft = 0
    every { ai.shaftOre.isDugOut(1L) } returns true

    ai.tickUntil { workplaceOf(miner) == null }
    assertEquals(home, ai.beliefOf(miner, BestiaDomain.HOME_POSITION))
  }

  @Test
  fun `Mining makes a miner faster`() {
    assertEquals(300f, ShaftWorkSystem.secondsPerVoxelAt(0))
    assertEquals(150f, ShaftWorkSystem.secondsPerVoxelAt(5))
  }

  /** The real shaft work, for a master at Mining 9 who digs a voxel in 30 seconds. */
  private fun shaftWork(): ShaftWorkSystem {
    val crafters = Crafters(StarterKit())
    val profiles = mockk<CrafterProfiles> {
      every { knowledgeOf(any()) } answers { crafters.Crafter(Master(MASTER, "Delver"), null, null, null) }
    }
    val mining = mockk<MiningSkill> { every { levelOf(any()) } returns 9 }

    return ShaftWorkSystem(ai.structureRegistry, ai.stationWorkers, profiles, ConnectionInfoService(), mining, ore)
  }

  private companion object {
    const val MASTER = 1L
    const val ORE = 4712L
    const val ORE_WEIGHT = 300
  }
}
