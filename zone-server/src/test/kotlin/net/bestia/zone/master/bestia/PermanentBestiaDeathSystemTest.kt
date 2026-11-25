package net.bestia.zone.master.bestia

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test

class PermanentBestiaDeathSystemTest {

  private val world = testWorld()
  private val loss = mockk<PlayerBestiaLoss>(relaxed = true)
  private val sut = PermanentBestiaDeathSystem(loss)

  @Test
  fun `an overkilled bestia is lost, one merely killed lies there`() {
    val overkilled = fallenBestia(lostForGood = true)
    val killed = fallenBestia(lostForGood = false)

    sut.update(world, 0f)

    verify(exactly = 1) { loss.lose(world, overkilled) }
    verify(exactly = 0) { loss.lose(world, killed) }
  }

  private fun fallenBestia(lostForGood: Boolean): EntityId {
    return world.createEntity { id ->
      add(id, OwnedBestia(masterId = 1L, playerBestiaId = id))
      add(id, Account(2L))
      add(id, Dead().also { it.lostForGood = lostForGood })
    }
  }
}
