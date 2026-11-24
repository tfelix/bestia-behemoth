package net.bestia.zone.bestiastorage

import net.bestia.bnet.proto.BestiaStorageSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.SelfSMSG

data class BestiaStorageSMSG(
  val bestias: List<StoredBestia>,
  val storageSlotCount: Int,
  val activeSlotCount: Int,
  val activeBestiaCount: Int,
) : SMSG {

  data class StoredBestia(
    val info: SelfSMSG.BestiaInfo,
    val restingSeconds: Long,
    val health: Pool,
    val mana: Pool,
    val stamina: Pool,
  )

  data class Pool(val current: Int, val max: Int) {
    fun toBnet(): BestiaStorageSmsgProto.BestiaStorageSMSG.Pool {
      return BestiaStorageSmsgProto.BestiaStorageSMSG.Pool.newBuilder()
        .setCurrent(current)
        .setMax(max)
        .build()
    }
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val storage = BestiaStorageSmsgProto.BestiaStorageSMSG.newBuilder()
      .setStorageSlotCount(storageSlotCount)
      .setActiveSlotCount(activeSlotCount)
      .setActiveBestiaCount(activeBestiaCount)
    bestias.forEach { bestia ->
      storage.addBestias(
        BestiaStorageSmsgProto.BestiaStorageSMSG.StoredBestia.newBuilder()
          .setInfo(bestia.info.toBnet())
          .setRestingSeconds(bestia.restingSeconds.toInt())
          .setHealth(bestia.health.toBnet())
          .setMana(bestia.mana.toBnet())
          .setStamina(bestia.stamina.toBnet())
      )
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setBestiaStorage(storage)
      .build()
  }
}
