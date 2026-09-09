package net.bestia.zone.world.stream

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.WorldInfoSMSGProto
import net.bestia.worldgen.voxel.ChunkEngine
import net.bestia.zone.environment.time.BestiaDateTime
import net.bestia.zone.message.SMSG
import net.bestia.zone.world.PersistedWorld

/**
 * The world's identity, its extent, and the clock. Sent once per connection, before any chunk.
 *
 * The geometry that used to be here is compiled into the client now - see `ClientWorldContract`. The extent
 * remains because it is the one part of a world's shape that genuinely varies.
 *
 * The seed is not in here and must not be added while the client receives only merged chunks: it has no
 * use for it without a base generator, and it is precisely what would turn prospecting into arithmetic.
 */
data class WorldInfoSMSG(
  val name: String,
  val widthCells: Int,
  val heightCells: Int,
  val chunkEngineVersion: Int,
  val worldAgeBestiaSeconds: Double,
  val timeSpeedFactor: Double
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val info = WorldInfoSMSGProto.WorldInfoSMSG.newBuilder()
      .setName(name)
      .setWidthCells(widthCells)
      .setHeightCells(heightCells)
      .setChunkEngineVersion(chunkEngineVersion)
      .setWorldAgeBestiaSeconds(worldAgeBestiaSeconds)
      .setTimeSpeedFactor(timeSpeedFactor)
      .setHoursPerDay(BestiaDateTime.HOURS_PER_DAY)
      .setDaysPerMonth(BestiaDateTime.DAYS_PER_MONTH)
      .setMonthsPerYear(BestiaDateTime.MONTHS_PER_YEAR)
      .setNightEndHour(BestiaDateTime.NIGHT_END_HOUR)
      .setDawnEndHour(BestiaDateTime.DAWN_END_HOUR)
      .setDuskStartHour(BestiaDateTime.DUSK_START_HOUR)
      .setNightStartHour(BestiaDateTime.NIGHT_START_HOUR)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setWorldInfo(info)
      .build()
  }

  companion object {
    /**
     * Built from the *stored* record for identity and extent, and from this build for the engine version.
     *
     * The record owns what the world is; the build owns what this process can generate. The running
     * `WorldConfig` is no longer read here at all - everything it used to contribute is now something the
     * client compiles in and the boot gate guarantees.
     */
    fun of(
      record: PersistedWorld,
      now: BestiaDateTime,
      timeSpeedFactor: Double
    ) = WorldInfoSMSG(
      name = record.name,
      widthCells = record.widthCells,
      heightCells = record.heightCells,
      chunkEngineVersion = ChunkEngine.VERSION,
      worldAgeBestiaSeconds = now.absoluteSecond.toDouble(),
      timeSpeedFactor = timeSpeedFactor
    )
  }
}
