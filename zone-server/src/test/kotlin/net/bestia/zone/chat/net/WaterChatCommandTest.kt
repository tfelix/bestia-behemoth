package net.bestia.zone.chat.net

import io.mockk.mockk
import io.mockk.verify
import net.bestia.account.Authority
import net.bestia.zone.water.WaterService
import net.bestia.zone.world.stream.ChunkStreamConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WaterChatCommandTest {

  private val water = mockk<WaterService>(relaxed = true)

  private fun run(text: String, settings: ChunkStreamConfig = ChunkStreamConfig()): Boolean {
    return WaterChatCommand(water, settings).tryExecute(PLAYER, text, setOf(Authority.TERRAIN))
  }

  @Test
  fun `a pour without a radius uses the default`() {
    assertTrue(run("/water 10 -20 5"))

    verify { water.requestPour(10, -20, 5, 3) }
  }

  @Test
  fun `a pour takes a radius`() {
    assertTrue(run("/water 10 20 -5 8"))

    verify { water.requestPour(10, 20, -5, 8) }
  }

  @Test
  fun `a radius past the cap is refused`() {
    assertFalse(run("/water 1 2 3 ${WaterService.MAX_POUR_RADIUS + 1}"))
    assertFalse(run("/water 1 2 3 0"))

    verify(exactly = 0) { water.requestPour(any(), any(), any(), any()) }
  }

  @Test
  fun `nothing is poured while debug edits are off`() {
    assertFalse(run("/water 1 2 3", ChunkStreamConfig(allowDebugEdits = false)))

    verify(exactly = 0) { water.requestPour(any(), any(), any(), any()) }
  }

  private companion object {
    const val PLAYER = 7L
  }
}
