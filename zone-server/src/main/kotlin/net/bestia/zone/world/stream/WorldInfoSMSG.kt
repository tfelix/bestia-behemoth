package net.bestia.zone.world.stream

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.WorldInfoSMSGProto
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.store.PipelineVersion
import net.bestia.zone.message.SMSG
import net.bestia.zone.world.PersistedWorld

/**
 * The world's shape and identity. Sent once per connection, before any chunk.
 *
 * The seed is not in here and must not be added while the client receives only merged chunks: it has no
 * use for it without a base generator, and it is precisely what would turn prospecting into arithmetic.
 */
data class WorldInfoSMSG(
  val name: String,
  val widthCells: Int,
  val heightCells: Int,
  val cellSizeMetres: Double,
  val chunkSize: Int,
  val chunkHeight: Int,
  val voxelSizeMetres: Double,
  val seaLevelMetres: Double,
  val wrapX: Boolean,
  val wrapY: Boolean,
  val pipelineVersion: Long,
  val blockPaletteVersion: Long,
  val chunkFormatVersion: Int,
  val viewRadiusChunks: Int
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val info = WorldInfoSMSGProto.WorldInfoSMSG.newBuilder()
      .setName(name)
      .setWidthCells(widthCells)
      .setHeightCells(heightCells)
      .setCellSizeMetres(cellSizeMetres)
      .setChunkSize(chunkSize)
      .setChunkHeight(chunkHeight)
      .setVoxelSizeMetres(voxelSizeMetres)
      .setSeaLevelMetres(seaLevelMetres)
      .setWrapX(wrapX)
      .setWrapY(wrapY)
      .setPipelineVersion(pipelineVersion)
      .setBlockPaletteVersion(blockPaletteVersion)
      .setChunkFormatVersion(chunkFormatVersion)
      .setViewRadiusChunks(viewRadiusChunks)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setWorldInfo(info)
      .build()
  }

  companion object {
    /**
     * Built from the *stored* record for identity and dimensions and from the *running* build for the
     * version vector.
     *
     * They agree or the server refused to boot ([net.bestia.zone.world.WorldService.load]), so which one
     * a field comes from is a matter of which is the authority rather than of which happens to be handy:
     * the record owns what the world is, the build owns what this process can generate.
     */
    fun of(
      record: PersistedWorld,
      config: WorldConfig,
      version: PipelineVersion,
      viewRadiusChunks: Int
    ) = WorldInfoSMSG(
      name = record.name,
      widthCells = record.widthCells,
      heightCells = record.heightCells,
      cellSizeMetres = record.cellSizeMetres,
      chunkSize = config.chunkSize,
      chunkHeight = config.chunkHeight,
      voxelSizeMetres = config.voxelSize,
      seaLevelMetres = config.seaLevel,
      wrapX = config.wrapX,
      wrapY = config.wrapY,
      pipelineVersion = version.pipelineVersion,
      blockPaletteVersion = version.blockPaletteVersion,
      chunkFormatVersion = version.chunkFormatVersion,
      viewRadiusChunks = viewRadiusChunks
    )
  }
}
