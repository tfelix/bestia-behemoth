package net.bestia.zone.engine

import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.ground.GroundLevelStore
import net.bestia.zone.world.stream.ChunkEditJournal
import org.junit.jupiter.api.Test
import net.bestia.zone.persistence.EntityPersistenceService
import net.bestia.zone.water.WaterService

class PersistOnShutdownTest {

  private val world = testWorld()
  private val engine = mockk<ZoneEngine>(relaxed = true)
  private val worldView = mockk<WorldView>().also {
    every { it.read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(world) }
  }
  private val persistence = mockk<EntityPersistenceService>(relaxed = true)
  private val economy = mockk<SettlementEconomyService>(relaxed = true)
  private val reserve = mockk<WorldReserve>(relaxed = true)
  private val water = mockk<WaterService>(relaxed = true)
  private val chunkEdits = mockk<ChunkEditJournal>(relaxed = true)
  private val wear = mockk<GroundLevelStore>(relaxed = true)
  private val blood = mockk<GroundLevelStore>(relaxed = true)
  private val executor = mockk<AsyncJobExecutor>(relaxed = true)

  private val sut =
    PersistOnShutdown(
      engine, worldView, persistence, economy, reserve, water, chunkEdits, listOf(wear, blood), executor
    )

  @Test
  fun `the tick stops, then everything is saved, then the writes are given time to land`() {
    sut.start()

    sut.stop()

    verifyOrder {
      engine.stop()
      persistence.syncAll(world)
      economy.flush()
      reserve.flush()
      water.commitAll()
      chunkEdits.flushDirty()
      wear.flushDirty()
      blood.flushDirty()
      executor.shutdown(timeoutSeconds = any())
    }
  }
}
