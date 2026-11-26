package net.bestia.zone.doomsday

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.WorldEndSmsgProto
import net.bestia.zone.message.SMSG

/** The world ends: every client plays its final sequence and shows what the world lived through. */
data class WorldEndSMSG(
  val ending: DoomEnding,
  val worldName: String,
  val threat: DoomKind?,
  val daysLived: Int,
  val tPercent: Int,
  val townsLost: Int,
  val secondsLeft: Int,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = WorldEndSmsgProto.WorldEndSMSG.newBuilder()
      .setEnding(WorldEndSmsgProto.WorldEnding.valueOf("WORLD_ENDING_${ending.name}"))
      .setWorldName(worldName)
      .setThreat(threat?.name ?: "")
      .setDaysLived(daysLived)
      .setTPercent(tPercent)
      .setTownsLost(townsLost)
      .setSecondsLeft(secondsLeft)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setWorldEnd(proto)
      .build()
  }
}
