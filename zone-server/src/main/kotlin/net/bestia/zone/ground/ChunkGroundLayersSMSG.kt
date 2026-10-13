package net.bestia.zone.ground

import com.google.protobuf.ByteString
import net.bestia.bnet.proto.ChunkGroundLayersSMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.SMSG
import net.bestia.zone.world.stream.ChunkCoords

/**
 * The layers of lasting marks that changed on one chunk column, or that a client has not been told about yet.
 *
 * Rides behind the chunk payload and is dropped with it, exactly as [net.bestia.zone.world.stream.ChunkStaticEntitiesSMSG] is. See the
 * proto for why the layers share one message and why none of this can be a block type.
 *
 * @property layers one entry per layer this message speaks about. Each replaces that layer on the client, an
 *   empty one retires it, and a layer not listed is left alone.
 */
data class ChunkGroundLayersSMSG(
  val chunk: ChunkPos,
  val layers: List<GroundLayerEntry>
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val message = ChunkGroundLayersSMSGProto.ChunkGroundLayersSMSG.newBuilder()
      .setPos(ChunkCoords.toProto(chunk))

    // Sorted, so two equal columns encode to equal bytes and the fan-out cache cannot be defeated by the order
    // the entries were gathered in.
    layers.sortedBy { it.layer.wireId }.forEach { entry ->
      message.addLayers(
        ChunkGroundLayersSMSGProto.GroundLayerCells.newBuilder()
          .setLayer(layerIdOf(entry.layer))
          .setEncoding(encodingOf(entry.encoding))
          .setCells(ByteString.copyFrom(entry.cells))
          .setLastDecayedSecond(entry.lastDecayedSecond)
      )
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setChunkGroundLayers(message.build())
      .build()
  }

  /**
   * Deliberately terse, for [ChunkGroundOverlaySMSG]'s reason: `net.bestia.zone` runs at TRACE in development
   * and protobuf's own `toString` escapes every byte, so a few hundred bytes of cells stringify to several times
   * that. This message also belongs in `socket.filter-log-messages`.
   */
  override fun toString(): String {
    return "ChunkGroundLayersSMSG($chunk, ${layers.size} layer(s), ${layers.sumOf { it.cells.size }}B)"
  }

  private companion object {

    fun layerIdOf(layer: GroundLayer): ChunkGroundLayersSMSGProto.GroundLayerId {
      // By number rather than by name: GroundLayer.wireId is the contract, and a rename on either side should
      // be a compile error or nothing, never a silently different channel.
      return ChunkGroundLayersSMSGProto.GroundLayerId.forNumber(layer.wireId)
        ?: error("no wire id ${layer.wireId} for $layer; proto and GroundLayer have diverged")
    }

    fun encodingOf(encoding: GroundLayerEncoding): ChunkGroundLayersSMSGProto.ChunkGroundLayerEncoding {
      return ChunkGroundLayersSMSGProto.ChunkGroundLayerEncoding.forNumber(encoding.wireId)
        ?: error("no wire id ${encoding.wireId} for $encoding; proto and GroundLayerEncoding have diverged")
    }
  }
}
