package net.bestia.zone.auction

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.TradeLinkNoticeSmsgProto
import net.bestia.zone.message.SMSG

data class TradeLinkNoticeSMSG(val notice: Notice, val partnerName: String) : SMSG {

  enum class Notice(val proto: TradeLinkNoticeSmsgProto.TradeLinkNoticeSMSG.Notice) {
    LINKED(TradeLinkNoticeSmsgProto.TradeLinkNoticeSMSG.Notice.LINKED),
    CUT(TradeLinkNoticeSmsgProto.TradeLinkNoticeSMSG.Notice.CUT),
    DECLINED(TradeLinkNoticeSmsgProto.TradeLinkNoticeSMSG.Notice.DECLINED),
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val msg = TradeLinkNoticeSmsgProto.TradeLinkNoticeSMSG.newBuilder()
      .setNotice(notice.proto)
      .setPartnerName(partnerName)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setTradeLinkNotice(msg)
      .build()
  }
}
