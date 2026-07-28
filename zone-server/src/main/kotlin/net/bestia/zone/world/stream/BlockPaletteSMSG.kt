package net.bestia.zone.world.stream

import net.bestia.bnet.proto.BlockPaletteSMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.worldgen.voxel.BlockType
import net.bestia.zone.message.SMSG

/**
 * The block palette, so nothing about it is hardcoded on the client.
 *
 * Sent once per connection with the world info. Ordered by id rather than by declaration, which matches
 * how `PipelineVersion.paletteVersion` folds the palette - and means the message is stable under a
 * reordering of the enum, exactly like the version hash it accompanies.
 */
data class BlockPaletteSMSG(
  val blocks: List<Entry>
) : SMSG {

  data class Entry(val id: Int, val name: String, val solid: Boolean, val opaque: Boolean)

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val palette = BlockPaletteSMSGProto.BlockPaletteSMSG.newBuilder()

    blocks.forEach { block ->
      palette.addBlocks(
        BlockPaletteSMSGProto.BlockPaletteEntry.newBuilder()
          .setId(block.id)
          .setName(block.name)
          .setSolid(block.solid)
          .setOpaque(block.opaque)
      )
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setBlockPalette(palette.build())
      .build()
  }

  companion object {
    fun current() = BlockPaletteSMSG(
      BlockType.entries
        .sortedBy { it.id }
        .map { Entry(id = it.id, name = it.name, solid = it.solid, opaque = it.opaque) }
    )
  }
}
