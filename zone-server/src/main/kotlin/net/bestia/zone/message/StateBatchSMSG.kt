package net.bestia.zone.message

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.StateBatchSmsgProto

/** Every entity state change one account is told about in one tick, sent as one frame. */
class StateBatchSMSG(
  val serverTick: Long,
  val updates: List<EntityUpdate>,
) : SMSG {

  /** Every message in the batch, in order. */
  val messages: List<EntitySMSG>
    get() = updates.flatMap { it.messages }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val batch = StateBatchSmsgProto.StateBatchSMSG.newBuilder().setServerTick(serverTick)
    updates.forEach { batch.addUpdates(it.proto) }

    return EnvelopeProto.Envelope.newBuilder().setStateBatch(batch).build()
  }

  /**
   * The same bytes as [toBnetEnvelope] would serialise to, built from each update's [EntityUpdate.encoded] so a
   * shared update is not serialised again. An embedded message and a `bytes` field look the same on the wire.
   */
  fun envelopeSize(): Int {
    return CodedOutputStream.computeTagSize(EnvelopeProto.Envelope.STATE_BATCH_FIELD_NUMBER) +
      CodedOutputStream.computeUInt32SizeNoTag(batchSize) +
      batchSize
  }

  /** Writes what [envelopeSize] measures; see there. */
  fun writeEnvelope(out: CodedOutputStream) {
    out.writeTag(EnvelopeProto.Envelope.STATE_BATCH_FIELD_NUMBER, WireFormat.WIRETYPE_LENGTH_DELIMITED)
    out.writeUInt32NoTag(batchSize)
    if (serverTick != 0L) {
      out.writeUInt64(StateBatchSmsgProto.StateBatchSMSG.SERVER_TICK_FIELD_NUMBER, serverTick)
    }
    updates.forEach { out.writeBytes(StateBatchSmsgProto.StateBatchSMSG.UPDATES_FIELD_NUMBER, it.encoded) }
  }

  private val batchSize: Int by lazy {
    val tickSize = if (serverTick == 0L) {
      0
    } else {
      CodedOutputStream.computeUInt64Size(StateBatchSmsgProto.StateBatchSMSG.SERVER_TICK_FIELD_NUMBER, serverTick)
    }

    tickSize + updates.sumOf {
      CodedOutputStream.computeBytesSize(StateBatchSmsgProto.StateBatchSMSG.UPDATES_FIELD_NUMBER, it.encoded)
    }
  }

  companion object {
    /** For state sent in answer to a request rather than by the tick's sync. */
    fun outsideTick(updates: List<EntityUpdate>): StateBatchSMSG {
      return StateBatchSMSG(serverTick = 0, updates = updates)
    }
  }
}
