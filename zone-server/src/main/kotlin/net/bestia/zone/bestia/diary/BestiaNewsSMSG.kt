package net.bestia.zone.bestia.diary

import net.bestia.bnet.proto.BestiaNewsSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.PlayerBestiaId

data class BestiaNewsSMSG(
  val playerBestiaId: PlayerBestiaId,
  val kind: BestiaHappened.Kind,
  val atEpochMs: Long,
  val items: Map<String, Int> = emptyMap(),
  val place: String? = null,
  val reason: String? = null,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val news = BestiaNewsSmsgProto.BestiaNewsSMSG.newBuilder()
      .setPlayerBestiaId(playerBestiaId)
      .setKind(kind.name)
      .putAllItems(items)
      .setPlace(place.orEmpty())
      .setReason(reason.orEmpty())
      .setAtEpochMs(atEpochMs)

    return EnvelopeProto.Envelope.newBuilder()
      .setBestiaNews(news)
      .build()
  }
}
