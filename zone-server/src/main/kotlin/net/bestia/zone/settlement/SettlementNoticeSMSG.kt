package net.bestia.zone.settlement

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SettlementNoticeSmsgProto
import net.bestia.zone.message.SMSG

data class SettlementNoticeSMSG(val notice: Notice, val name: String) : SMSG {

  enum class Notice(val proto: SettlementNoticeSmsgProto.SettlementNoticeSMSG.Notice) {
    FOUNDED(SettlementNoticeSmsgProto.SettlementNoticeSMSG.Notice.FOUNDED),
    FELL(SettlementNoticeSmsgProto.SettlementNoticeSMSG.Notice.FELL),
    TORN_DOWN(SettlementNoticeSmsgProto.SettlementNoticeSMSG.Notice.TORN_DOWN),
    HANDED_OVER(SettlementNoticeSmsgProto.SettlementNoticeSMSG.Notice.HANDED_OVER),
    RECEIVED(SettlementNoticeSmsgProto.SettlementNoticeSMSG.Notice.RECEIVED),
    SITE_LOST(SettlementNoticeSmsgProto.SettlementNoticeSMSG.Notice.SITE_LOST),
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val msg = SettlementNoticeSmsgProto.SettlementNoticeSMSG.newBuilder()
      .setNotice(notice.proto)
      .setName(name)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setSettlementNotice(msg)
      .build()
  }
}
