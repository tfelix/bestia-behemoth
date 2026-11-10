package net.bestia.zone.crafting.station

import net.bestia.zone.prop.station.QueueEntry
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.StationStateSmsgProto
import net.bestia.zone.message.SMSG
import kotlin.math.roundToInt

/** A station's queue and held items, for its owner's crafting window. */
data class StationStateSMSG(
  val structureId: Long,
  val queue: List<QueueEntry>,
  val held: Map<Long, Int>,
  val progressSeconds: Float,
  val craftSeconds: Float,
  val pause: StationPause
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val entries = queue.mapIndexed { index, entry ->
      val first = index == 0
      StationStateSmsgProto.StationQueueEntry.newBuilder()
        .setRecipeId(entry.recipeId)
        .setAmount(entry.amount)
        .setDone(entry.done)
        .setProgressMillis(if (first) (progressSeconds * 1000).roundToInt() else 0)
        .setCraftMillis(if (first) (craftSeconds * 1000).roundToInt() else 0)
        .build()
    }
    val piles = held.map { (itemId, amount) ->
      StationStateSmsgProto.StationHeldItem.newBuilder().setItemId(itemId.toInt()).setAmount(amount).build()
    }

    val message = StationStateSmsgProto.StationStateSMSG.newBuilder()
      .setStructureId(structureId)
      .addAllEntries(entries)
      .addAllHeld(piles)
      .setPause(pause.wire)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setStationState(message)
      .build()
  }
}
