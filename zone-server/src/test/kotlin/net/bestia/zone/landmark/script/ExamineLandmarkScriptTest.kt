package net.bestia.zone.landmark.script

import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.poi.PoiKind
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Landmark
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import org.junit.jupiter.api.Test

class ExamineLandmarkScriptTest {

  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val script = ExamineLandmarkScript(messages)
  private val world = testWorld()

  private val obelisk = world.createEntity { id ->
    add(id, Position.fromVec3(Vec3L(100, 100, 10)))
    add(id, Landmark(PoiKind.BROKEN_OBELISK, copy = 0))
  }

  @Test
  fun `a click within reach names the landmark to the clicker`() {
    val reader = playerAt(Vec3L(103, 100, 10))

    script.onInteract(world, reader, obelisk)

    verify { messages.sendToPlayer(ACCOUNT_ID, match<ChatSMSG> { "broken obelisk" in it.text }) }
  }

  @Test
  fun `a click from far off says nothing`() {
    val reader = playerAt(Vec3L(200, 100, 10))

    script.onInteract(world, reader, obelisk)

    verify(exactly = 0) { messages.sendToPlayer(any(), any<ChatSMSG>()) }
  }

  private fun playerAt(at: Vec3L): Long {
    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, Account(ACCOUNT_ID))
    }
  }

  private companion object {
    const val ACCOUNT_ID = 42L
  }
}
