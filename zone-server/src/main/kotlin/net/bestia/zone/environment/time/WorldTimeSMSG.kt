package net.bestia.zone.environment.time

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.WorldTimeSMSGProto
import net.bestia.zone.message.SMSG

/**
 * Re-anchors a client's calendar after the server's clock has moved discontinuously.
 *
 * Not sent on a timer, and it must not become one: the client is given an anchor and the rate to run it
 * forward at, which costs one message per connection instead of one per second per player. This covers the
 * only thing that anchor cannot - the reading jumping - which today means [BestiaClock.jumpTo] behind
 * `/date`.
 *
 * `WorldInfoSMSG` carries the same two numbers and is deliberately *not* reused: it also states the world's
 * identity, and a client that receives it discards every chunk it holds. Moving a clock with it would tear
 * down and re-stream the terrain.
 */
data class WorldTimeSMSG(
  val worldAgeBestiaSeconds: Double,
  val timeSpeedFactor: Double
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val time = WorldTimeSMSGProto.WorldTimeSMSG.newBuilder()
      .setWorldAgeBestiaSeconds(worldAgeBestiaSeconds)
      .setTimeSpeedFactor(timeSpeedFactor)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setWorldTime(time)
      .build()
  }

  companion object {
    fun of(now: BestiaDateTime, timeSpeedFactor: Double) = WorldTimeSMSG(
      worldAgeBestiaSeconds = now.absoluteSecond.toDouble(),
      timeSpeedFactor = timeSpeedFactor
    )
  }
}
