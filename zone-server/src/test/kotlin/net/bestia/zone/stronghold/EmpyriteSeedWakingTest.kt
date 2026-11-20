package net.bestia.zone.stronghold

import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EmpyriteSeedWakingTest {

  private val waking = EmpyriteSeedWaking(
    mockk(), mockk(), mockk(), mockk(), StrongholdConfig(minAwakeSeeds = 4, guildsPerSeed = 25)
  )

  @Test
  fun `a few approved guilds still find the minimum awake`() {
    assertEquals(4, waking.target(0))
    assertEquals(4, waking.target(100))
  }

  @Test
  fun `every started 25 approved guilds wake one more seed`() {
    assertEquals(5, waking.target(101))
    assertEquals(5, waking.target(125))
    assertEquals(6, waking.target(126))
  }
}
