package net.bestia.zone.guild

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.GuildNoticeSmsgProto
import net.bestia.zone.message.SMSG

data class GuildNoticeSMSG(
  val notice: Notice,
) : SMSG {

  enum class Notice { BANNER_PLANTED, BANNER_FALLEN, BANNER_FADED }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = GuildNoticeSmsgProto.GuildNoticeSMSG.newBuilder()
      .setNotice(
        when (notice) {
          Notice.BANNER_PLANTED -> GuildNoticeSmsgProto.GuildNotice.GUILD_NOTICE_BANNER_PLANTED
          Notice.BANNER_FALLEN -> GuildNoticeSmsgProto.GuildNotice.GUILD_NOTICE_BANNER_FALLEN
          Notice.BANNER_FADED -> GuildNoticeSmsgProto.GuildNotice.GUILD_NOTICE_BANNER_FADED
        }
      )
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setGuildNotice(proto)
      .build()
  }
}
