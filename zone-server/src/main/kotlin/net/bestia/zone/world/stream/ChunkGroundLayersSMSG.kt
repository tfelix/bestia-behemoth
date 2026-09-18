package net.bestia.zone.world.stream

import com.google.protobuf.ByteString
import net.bestia.bnet.proto.ChunkGroundLayersSMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.SMSG
import net.bestia.zone.world.ground.GroundLayer
import net.bestia.zone.world.ground.GroundStamp

/**
 * Everything lasting that has happened to the ground of one chunk column.
 *
 * Rides behind the chunk payload and is dropped with it, exactly as [ChunkStaticEntitiesSMSG] is. See the
 * proto for why the layers share one message and why none of this can be a block type.
 *
 * @property cells one entry per layer this column has anything of, each `chunkSize²` nibbles
 * @property stamps marks with a shape and a heading, which a grid of levels cannot express
 */
data class ChunkGroundLayersSMSG(
  val chunk: ChunkPos,
  val cells: Map<GroundLayer, ByteArray>,
  val stamps: List<GroundStamp>
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val layers = ChunkGroundLayersSMSGProto.ChunkGroundLayersSMSG.newBuilder()
      .setPos(ChunkCoords.toProto(chunk))

    // Sorted, so two equal columns encode to equal bytes and the fan-out cache cannot be defeated by a map's
    // iteration order.
    cells.entries.sortedBy { it.key.wireId }.forEach { (layer, nibbles) ->
      layers.addLayers(
        ChunkGroundLayersSMSGProto.GroundLayerCells.newBuilder()
          .setLayer(layerIdOf(layer))
          .setEncoding(
            ChunkGroundLayersSMSGProto.ChunkGroundLayerEncoding.CHUNK_GROUND_LAYER_ENCODING_NIBBLE_V1
          )
          .setCells(ByteString.copyFrom(nibbles))
      )
    }

    stamps.forEach { stamp ->
      layers.addStamps(
        ChunkGroundLayersSMSGProto.GroundStamp.newBuilder()
          .setX(stamp.x)
          .setY(stamp.y)
          .setLayer(layerIdOf(stamp.layer))
          .setBrush(stamp.brush.wireId)
          .setRotation(stamp.rotation)
          .setSeed(stamp.seed)
          .setAtSecond(stamp.atSecond)
      )
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setChunkGroundLayers(layers.build())
      .build()
  }

  /**
   * Deliberately terse, for [ChunkGroundOverlaySMSG]'s reason: `net.bestia.zone` runs at TRACE in development
   * and protobuf's own `toString` escapes every byte, so half a kilobyte of cells stringifies to several.
   * This message also belongs in `socket.filter-log-messages`.
   */
  override fun toString() =
    "ChunkGroundLayersSMSG($chunk, ${cells.size} layer(s), ${cells.values.sumOf { it.size }}B, " +
        "${stamps.size} stamp(s))"

  // `ByteArray` gives data classes reference equality, which would make two identical payloads compare
  // unequal. See ChunkGroundOverlaySMSG: a message type whose equals lies is a trap for whoever trusts it.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChunkGroundLayersSMSG) return false
    if (chunk != other.chunk || stamps != other.stamps) return false
    if (cells.keys != other.cells.keys) return false
    return cells.all { (layer, nibbles) -> nibbles.contentEquals(other.cells[layer]) }
  }

  override fun hashCode(): Int {
    var result = chunk.hashCode()
    result = 31 * result + stamps.hashCode()
    cells.entries.sortedBy { it.key.wireId }.forEach { (layer, nibbles) ->
      result = 31 * result + layer.hashCode()
      result = 31 * result + nibbles.contentHashCode()
    }
    return result
  }

  private companion object {

    fun layerIdOf(layer: GroundLayer): ChunkGroundLayersSMSGProto.GroundLayerId {
      // By number rather than by name: GroundLayer.wireId is the contract, and a rename on either side should
      // be a compile error or nothing, never a silently different channel.
      return ChunkGroundLayersSMSGProto.GroundLayerId.forNumber(layer.wireId)
        ?: error("no wire id ${layer.wireId} for $layer; proto and GroundLayer have diverged")
    }
  }
}
