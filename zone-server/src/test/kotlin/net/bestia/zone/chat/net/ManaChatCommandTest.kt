package net.bestia.zone.chat.net

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.account.Role
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.mana.ManaConcentrationService
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.ManaGround
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ManaChatCommandTest {

  private val world = testWorld()
  private val master = world.createEntity { id -> add(id, Position.fromVec3(Vec3L(300, 300, 0))) }

  private val worldView = mockk<WorldView> {
    every { modify<Any?>(any(), any()) } answers { secondArg<World.(EntityId) -> Any?>().invoke(world, firstArg()) }
  }
  private val connections = mockk<ConnectionInfoService> {
    every { getSelectedMasterEntityId(PLAYER) } returns master
  }
  private val ground = mockk<ManaGround> {
    every { startDensity(any()) } returns START
    every { potentialOf(any()) } returns 0.4
  }
  private val field = ManaField()
  private val mana = ManaConcentrationService(mockk(), field, ground)
  private val out = mockk<OutMessageProcessor>(relaxed = true)

  private val sut = ManaChatCommand(connections, mana, ground, worldView, out)

  @Test
  fun `set makes the density read back as given`() {
    sut.tryExecute(PLAYER, "/mana set 0.7", Role.GM.authorities)

    assertEquals(0.7, mana.densityAt(Vec3L(300, 300, 0)), 1e-9)
    assertEquals("Mana density 0.700, start 0.100, potential 0.40, cell (2, 2).", lastAnswer())
  }

  @Test
  fun `add releases motes over the radius`() {
    sut.tryExecute(PLAYER, "/mana add 50", Role.GM.authorities)

    assertEquals(START + 0.05, mana.densityAt(Vec3L(300, 300, 0)), 1e-9)
  }

  @Test
  fun `a radius beyond the limit is refused`() {
    assertFalse(sut.tryExecute(PLAYER, "/mana add 50 5000", Role.GM.authorities))
    assertEquals(0, field.tileCount)
  }

  @Test
  fun `a player cannot read the field`() {
    assertFalse(sut.tryExecute(PLAYER, "/mana", Role.USER.authorities))

    verify(exactly = 0) { out.sendToPlayer(any(), any<ChatSMSG>()) }
  }

  private fun lastAnswer(): String {
    val sent = slot<ChatSMSG>()
    verify { out.sendToPlayer(PLAYER, capture(sent)) }
    return sent.captured.text
  }

  private companion object {
    const val PLAYER = 7L
    const val START = 0.1
  }
}
