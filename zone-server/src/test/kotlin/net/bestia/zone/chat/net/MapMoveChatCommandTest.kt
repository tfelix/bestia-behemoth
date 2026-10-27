package net.bestia.zone.chat.net

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.account.Authority
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.place.ecs.AreaNameRegistry
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkStreamInbox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapMoveChatCommandTest {

  private val inbox = mockk<ChunkStreamInbox>(relaxed = true)
  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val worldService = mockk<WorldService> {
    every { config } returns WorldConfig(seed = 1, widthCells = 128, heightCells = 128)
  }
  private val areaNames = AreaNameRegistry().apply {
    add(AreaNameRegistry.Entry(id = -1, name = "Trestein", x = 61_000, y = 90_500, radius = 300))
  }

  private val command = MapMoveChatCommand(inbox, worldService, areaNames, out)

  private fun run(text: String): ChunkStreamInbox.Teleport {
    assertTrue(command.tryExecute(PLAYER, text, setOf(Authority.MAP_MOVE)))

    val teleport = slot<ChunkStreamInbox.Teleport>()
    verify { inbox.offerTeleport(capture(teleport)) }
    return teleport.captured
  }

  @Test
  fun `raw voxels still work`() {
    assertEquals(ChunkStreamInbox.Teleport(PLAYER, 64_000, 64_000), run("/mm 64000 64000"))
  }

  @Test
  fun `a grid reference lands in the middle of its cell`() {
    assertEquals(ChunkStreamInbox.Teleport(PLAYER, 61_350, 91_750), run("/mm H12 53 37"))
  }

  @Test
  fun `a settlement is found by name, whatever its case`() {
    assertEquals(ChunkStreamInbox.Teleport(PLAYER, 61_000, 90_500), run("/mm trestein"))
  }

  @Test
  fun `an unknown target is answered rather than reported as an unknown command`() {
    assertTrue(command.tryExecute(PLAYER, "/mm Atlantis", setOf(Authority.MAP_MOVE)))

    val reply = slot<SMSG>()
    verify { out.sendToPlayer(PLAYER, capture(reply)) }
    assertTrue((reply.captured as ChatSMSG).text.contains("Atlantis"))
    verify(exactly = 0) { inbox.offerTeleport(any()) }
  }

  private companion object {
    const val PLAYER = 7L
  }
}
