package net.bestia.zone.message

import net.bestia.bnet.proto.AnnouncementSmsgProto
import net.bestia.bnet.proto.EnvelopeProto

/** Something every connected player reads: in the chat, and on a banner at the top of the screen. */
data class AnnouncementSMSG(
  val announcement: Announcement,
  /** Substitution values for the client's text, such as a guild's and a Stronghold's name. */
  val args: List<String> = emptyList(),
) : SMSG {

  /** In the proto's order, after its UNSPECIFIED. */
  enum class Announcement {
    GM,
    STRONGHOLD_UNHELD,
    LAST_STAND,
    LAST_STAND_SOON,
    LAST_STAND_WON,
    CALLING,
    CALLING_SOON,
    DOOM_ONSET,
    DOOM_ESCALATION,
    DOOM_CATACLYSM,
    DOOM_RECEDES,
    FORESHADOW_BEGINS,
    FORESHADOW_HELD,
    FORESHADOW_FAILED,
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = AnnouncementSmsgProto.AnnouncementSMSG.newBuilder()
      .setAnnouncement(AnnouncementSmsgProto.Announcement.valueOf("ANNOUNCEMENT_${announcement.name}"))
      .addAllArgs(args)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setAnnouncement(proto)
      .build()
  }
}
