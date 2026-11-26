package net.bestia.zone.chat

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import org.junit.jupiter.api.Test

class TremorLinesTest {

  private val world = testWorld()
  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val sut = TremorLines(out)

  @Test
  fun `each master feels which way the ground trembled`() {
    master(accountId = 1, at = Vec3L(0, 0, 0))
    master(accountId = 2, at = Vec3L(20_000, 10_000, 0))
    master(accountId = 3, at = Vec3L(10_500, 0, 0))

    sut.feel(world, Vec3L(10_000, 0, 0))

    verify { out.sendToPlayer(1, ChatSMSG(ChatType.BROADCAST, "Far to the east, the ground trembles. Something old is awake.")) }
    verify { out.sendToPlayer(2, ChatSMSG(ChatType.BROADCAST, "Far to the south-west, the ground trembles. Something old is awake.")) }
    verify { out.sendToPlayer(3, ChatSMSG(ChatType.BROADCAST, "The ground heaves beneath you. Something old is awake.")) }
  }

  private fun master(accountId: Long, at: Vec3L) {
    world.createEntity { id ->
      add(id, Master(masterId = accountId, name = "M$accountId"))
      add(id, Account(accountId))
      add(id, Position.fromVec3(at))
    }
  }
}
