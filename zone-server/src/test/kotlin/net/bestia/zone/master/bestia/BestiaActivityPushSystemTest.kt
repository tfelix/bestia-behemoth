package net.bestia.zone.master.bestia

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ai.ecs.AiPipelineFixture
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BestiaActivityPushSystemTest {

  private lateinit var ai: AiPipelineFixture
  private lateinit var system: BestiaActivityPushSystem
  private var nowMs = 0L
  private var doing = BestiaActivity("GATHERING")
  private var playing = mapOf(ACCOUNT to MASTER)
  private val sent = mutableListOf<SMSG>()

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
    val activities = mockk<BestiaActivities> { every { of(any(), any(), any()) } answers { doing } }
    val connections = mockk<ConnectionInfoService> { every { activeMasters() } answers { playing } }
    val out = mockk<OutMessageProcessor> { every { sendToPlayer(ACCOUNT, any<SMSG>()) } answers { sent += secondArg<SMSG>() } }
    system = BestiaActivityPushSystem(connections, activities, out, now = { nowMs })

    val bestia = ai.spawnMob("passive_wanderer", Vec3L(0, 0, 0), orders = AiConfig(BestiaJob.GATHER))
    ai.world.add(bestia, OwnedBestia(masterId = MASTER, playerBestiaId = 1L))
  }

  private fun sweepAt(ms: Long) {
    nowMs = ms
    system.update(ai.world, 1f)
  }

  private fun kinds(): List<String> {
    return sent.map { (it as BestiaActivitySMSG).activity.kind }
  }

  @Test
  fun `a status line goes out once and again only when it changes`() {
    sweepAt(0)
    sweepAt(5_000)
    doing = BestiaActivity("HAULING")
    sweepAt(10_000)

    assertEquals(listOf("GATHERING", "HAULING"), kinds())
  }

  @Test
  fun `changes within two seconds wait, and the latest goes out`() {
    sweepAt(0)
    doing = BestiaActivity("HAULING")
    sweepAt(1_000)
    doing = BestiaActivity("EATING")
    sweepAt(1_500)
    sweepAt(2_000)

    assertEquals(listOf("GATHERING", "EATING"), kinds())
  }

  @Test
  fun `nothing goes to an owner who is not playing, and all of it once they are back`() {
    playing = emptyMap()
    sweepAt(0)
    assertEquals(emptyList<String>(), kinds())

    playing = mapOf(ACCOUNT to MASTER)
    sweepAt(1_000)
    assertEquals(listOf("GATHERING"), kinds())
  }

  private companion object {
    const val ACCOUNT = 3L
    const val MASTER = 7L
  }
}
