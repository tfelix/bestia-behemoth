package net.bestia.zone.world.stream

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.WorldInfoSMSGProto
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.voxel.ChunkEngine
import net.bestia.zone.environment.time.BestiaDateTime
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
  val chunkEngineVersion: Int,
  val viewRadiusChunks: Int,
  val worldAgeBestiaSeconds: Double,
  val timeSpeedFactor: Double
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
      .setChunkEngineVersion(chunkEngineVersion)
      .setViewRadiusChunks(viewRadiusChunks)
      .setWorldAgeBestiaSeconds(worldAgeBestiaSeconds)
      .setTimeSpeedFactor(timeSpeedFactor)
      .setHoursPerDay(BestiaDateTime.HOURS_PER_DAY)
      .setDaysPerMonth(BestiaDateTime.DAYS_PER_MONTH)
      .setMonthsPerYear(BestiaDateTime.MONTHS_PER_YEAR)
      .setNightHours(BestiaDateTime.NIGHT_HOURS)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setWorldInfo(info)
      .build()
  }

  companion object {
    /**
     * Built from the *stored* record for identity and dimensions, from the running config for geometry, and
     * from this build for the engine version.
     *
     * The record and the config agree or the server refused to boot
     * ([net.bestia.zone.world.WorldService.load]), so which one a field comes from is a matter of which is the
     * authority rather than of which happens to be handy: the record owns what the world is, the build owns
     * what this process can generate.
     */
    fun of(
      record: PersistedWorld,
      config: WorldConfig,
      viewRadiusChunks: Int,
      now: BestiaDateTime,
      timeSpeedFactor: Double
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
      chunkEngineVersion = ChunkEngine.VERSION,
      viewRadiusChunks = viewRadiusChunks,
      worldAgeBestiaSeconds = now.absoluteSecond.toDouble(),
      timeSpeedFactor = timeSpeedFactor
    )
  }
}
