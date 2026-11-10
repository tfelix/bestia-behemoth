package net.bestia.zone.breeding

import net.bestia.bnet.proto.BestiaEggProto
import net.bestia.bnet.proto.BreedingFarmSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import java.time.Instant

data class BreedingFarmSMSG(
  val structureId: Long,
  val phase: BreedingFarmSmsgProto.BreedingFarmPhase,
  val motherEntityId: Long,
  val fatherEntityId: Long,
  val hasBundle: Boolean,
  val remainingMillis: Long,
  val totalMillis: Long,
  val egg: BestiaEggProto.BestiaEgg?,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val farm = BreedingFarmSmsgProto.BreedingFarmSMSG.newBuilder()
      .setStructureId(structureId)
      .setPhase(phase)
      .setMotherEntityId(motherEntityId)
      .setFatherEntityId(fatherEntityId)
      .setHasBundle(hasBundle)
      .setRemainingMs(remainingMillis)
      .setTotalMs(totalMillis)
    egg?.let(farm::setEgg)

    return EnvelopeProto.Envelope.newBuilder()
      .setBreedingFarm(farm)
      .build()
  }

  companion object {
    fun of(farm: BreedingFarm, now: Instant, readsExactIvs: Boolean): BreedingFarmSMSG {
      return BreedingFarmSMSG(
        structureId = farm.structureId,
        phase = farm.phase.toBnet(),
        motherEntityId = farm.pair?.motherEntityId ?: 0,
        fatherEntityId = farm.pair?.fatherEntityId ?: 0,
        hasBundle = farm.hasBundle,
        remainingMillis = farm.remainingAt(now).toMillis(),
        totalMillis = farm.clutchTime.toMillis(),
        egg = farm.egg?.toBnet(readsExactIvs),
      )
    }
  }
}
