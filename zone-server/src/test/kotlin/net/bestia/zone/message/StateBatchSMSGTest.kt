package net.bestia.zone.message

import com.google.protobuf.CodedOutputStream
import net.bestia.zone.battle.ecs.status.HealthComponentSMSG
import net.bestia.zone.entity.VanishEntitySMSG
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.PositionSMSG
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class StateBatchSMSGTest {

  private val shared = EntityUpdate(1L, listOf(HealthComponentSMSG(1L, current = 7, max = 10)))
  private val own = EntityUpdate(
    2L,
    listOf(PositionSMSG(2L, Vec3L(-5, 3, -120)), HealthComponentSMSG(2L, current = 1, max = 2))
  )
  private val gone = EntityUpdate(3L, listOf(VanishEntitySMSG(3L, VanishEntitySMSG.VanishKind.DEATH)))

  @Test
  fun `the spliced bytes are the bytes protobuf would write`() {
    val batch = StateBatchSMSG(serverTick = 4711, updates = listOf(shared, own, gone))

    assertContentEquals(batch.toBnetEnvelope().toByteArray(), splice(batch))
  }

  @Test
  fun `a batch from outside the tick leaves the tick out, as protobuf does`() {
    val batch = StateBatchSMSG.outsideTick(listOf(shared))

    assertContentEquals(batch.toBnetEnvelope().toByteArray(), splice(batch))
  }

  @Test
  fun `an update is grouped under its entity id once`() {
    val update = StateBatchSMSG(serverTick = 1, updates = listOf(own)).toBnetEnvelope().stateBatch.updatesList.single()

    assertEquals(2L, update.entityId)
    assertEquals(2, update.componentsCount)
  }

  private fun splice(batch: StateBatchSMSG): ByteArray {
    val bytes = ByteArray(batch.envelopeSize())
    val out = CodedOutputStream.newInstance(bytes)
    batch.writeEnvelope(out)
    out.checkNoSpaceLeft()

    return bytes
  }
}
