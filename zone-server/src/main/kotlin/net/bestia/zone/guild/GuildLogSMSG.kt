package net.bestia.zone.guild

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.GuildLogSmsgProto
import net.bestia.zone.guild.persistence.GuildLogKind
import net.bestia.zone.message.SMSG

/** The guild's newest log entries, newest first. */
data class GuildLogSMSG(
  val entries: List<Entry>,
) : SMSG {

  data class Entry(
    val kind: GuildLogKind,
    val atEpochMillis: Long,
    val actorName: String,
    val targetName: String,
    val detail: String,
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = GuildLogSmsgProto.GuildLogSMSG.newBuilder()
      .addAllEntries(entries.map { entry ->
        GuildLogSmsgProto.GuildLogEntry.newBuilder()
          .setKind(toProto(entry.kind))
          .setAtEpochMs(entry.atEpochMillis)
          .setActorName(entry.actorName)
          .setTargetName(entry.targetName)
          .setDetail(entry.detail)
          .build()
      })
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setGuildLog(proto)
      .build()
  }

  companion object {
    /** By name, so a kind missing from the proto fails the first time it is sent rather than going out as 0. */
    fun toProto(kind: GuildLogKind): GuildLogSmsgProto.GuildLogKind {
      return GuildLogSmsgProto.GuildLogKind.valueOf("GUILD_LOG_KIND_${kind.name}")
    }
  }
}
