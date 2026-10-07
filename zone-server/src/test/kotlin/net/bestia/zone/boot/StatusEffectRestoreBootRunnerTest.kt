package net.bestia.zone.boot

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.battle.persistence.StatusEffectPersistenceService
import net.bestia.zone.ecs.core.testWorld
import kotlin.test.Test

class StatusEffectRestoreBootRunnerTest {

  private val world = testWorld()
  private val statusEffects = mockk<StatusEffectPersistenceService>(relaxed = true)
  private val masters = mockk<MasterRepository>()
  private val bestias = mockk<PlayerBestiaRepository>()

  private val runner = StatusEffectRestoreBootRunner(world, statusEffects, masters, bestias)

  @Test
  fun `stored effects that belong to nobody are dropped, those waiting for a login are kept`() {
    val loaded = world.createEntity { }
    every { statusEffects.loadAll() } returns emptyMap()
    every { statusEffects.storedOwners() } returns setOf(loaded, MASTER, BESTIA, GONE)
    every { masters.findEntityIdsIn(any()) } returns listOf(MASTER)
    every { bestias.findEntityIdsIn(any()) } returns listOf(BESTIA)

    runner.run()

    verify { statusEffects.deleteFor(listOf(GONE)) }
  }

  private companion object {
    const val MASTER = 9_001L
    const val BESTIA = 9_002L
    const val GONE = 9_003L
  }
}
