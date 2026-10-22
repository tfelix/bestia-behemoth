package net.bestia.zone.compendium

import net.bestia.bnet.proto.CompendiumSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.compendium.persistence.CompendiumEntry
import net.bestia.zone.message.SMSG
import java.time.Instant

data class CompendiumSMSG(
  val entries: List<Entry>,
) : SMSG {

  data class Entry(val speciesId: Long, val caught: Boolean, val firstSeenAt: Instant)

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val compendium = CompendiumSmsgProto.CompendiumSMSG.newBuilder()
    entries.forEach {
      compendium.addEntries(
        CompendiumSmsgProto.CompendiumEntry.newBuilder()
          .setSpeciesId(it.speciesId)
          .setCaught(it.caught)
          .setFirstSeenEpochMs(it.firstSeenAt.toEpochMilli())
      )
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setCompendium(compendium)
      .build()
  }

  companion object {
    fun of(rows: List<CompendiumEntry>): CompendiumSMSG {
      return CompendiumSMSG(rows.map { Entry(it.speciesId, it.caughtAt != null, it.firstSeenAt) })
    }
  }
}
