package net.bestia.zone.breeding

import net.bestia.bnet.proto.BestiaEggProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.IncubatorSmsgProto
import net.bestia.zone.message.SMSG
import java.time.Instant

data class IncubatorSMSG(
  val structureId: Long,
  val egg: BestiaEggProto.BestiaEgg?,
  val remainingMillis: Long,
  val waitingForSlot: Boolean,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val incubator = IncubatorSmsgProto.IncubatorSMSG.newBuilder()
      .setStructureId(structureId)
      .setRemainingMs(remainingMillis)
      .setWaitingForSlot(waitingForSlot)
    egg?.let(incubator::setEgg)

    return EnvelopeProto.Envelope.newBuilder()
      .setIncubator(incubator)
      .build()
  }

  companion object {
    fun of(incubator: Incubator, now: Instant, readsExactIvs: Boolean): IncubatorSMSG {
      return IncubatorSMSG(
        structureId = incubator.structureId,
        egg = incubator.egg?.toBnet(readsExactIvs, incubator.hatchedMillisAt(now)),
        remainingMillis = incubator.remainingAt(now).toMillis(),
        waitingForSlot = incubator.waitingForSlot,
      )
    }
  }
}
