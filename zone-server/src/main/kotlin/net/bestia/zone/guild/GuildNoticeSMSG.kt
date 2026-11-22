package net.bestia.zone.guild

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.GuildNoticeSmsgProto
import net.bestia.zone.message.SMSG

data class GuildNoticeSMSG(
  val notice: Notice,
  /** Substitution values for the client's text, such as a Stronghold's name. */
  val args: List<String> = emptyList(),
) : SMSG {

  /** In the proto's order, after its UNSPECIFIED. */
  enum class Notice {
    BANNER_PLANTED,
    BANNER_FALLEN,
    BANNER_FADED,
    EMPYRITE_PLANTED,
    STRONGHOLD_STANDS,
    EMPYRITE_UNDER_ATTACK,
    STRONGHOLD_CAPTURED,
    STRONGHOLD_LOST,
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = GuildNoticeSmsgProto.GuildNoticeSMSG.newBuilder()
      .setNotice(GuildNoticeSmsgProto.GuildNotice.valueOf("GUILD_NOTICE_${notice.name}"))
      .addAllArgs(args)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setGuildNotice(proto)
      .build()
  }
}
