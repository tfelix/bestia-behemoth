package net.bestia.zone.guild

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GuildExpLevyTest {

  private val world = testWorld()
  private val ledger = mockk<GuildExpLedger>(relaxed = true)
  private val sut = GuildExpLevy(ledger)

  private fun member(taxPercent: Int?) = world.createEntity { id ->
    add(id, Master(MASTER_ID))
    taxPercent?.let { add(id, GuildMembership(GUILD_ID, it)) }
  }

  @Test
  fun `the rank's share goes to the guild and the member keeps the rest`() {
    assertEquals(90, sut.levy(world, member(taxPercent = 10), 100))

    verify { ledger.add(GUILD_ID, MASTER_ID, 10L) }
  }

  @Test
  fun `the highest tax leaves the member a fifth`() {
    assertEquals(20, sut.levy(world, member(taxPercent = 80), 100))
  }

  @Test
  fun `the tax is rounded down, so a small share may pay none`() {
    assertEquals(9, sut.levy(world, member(taxPercent = 10), 9))

    verify(exactly = 0) { ledger.add(any(), any(), any()) }
  }

  @Test
  fun `a master in no guild keeps everything`() {
    assertEquals(100, sut.levy(world, member(taxPercent = null), 100))
  }

  private companion object {
    const val GUILD_ID = 3L
    const val MASTER_ID = 30L
  }
}
