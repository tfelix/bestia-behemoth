package net.bestia.zone.ground

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.GroundMarkRulesSMSGProto
import net.bestia.zone.message.SMSG

/**
 * The server's settings for the marks a client lays down itself: footprints, wear and blood.
 *
 * Sent once per connection, after `WorldInfoSMSG`. Built from the live configs and from the tables that decide
 * which ground takes a mark, so a retune on restart reaches clients with no change to them. See the proto for
 * what is still mirrored by hand.
 *
 * @property stampLifetimeMillis real milliseconds a fresh print lasts
 * @property stampBlockIds the block ids a footfall leaves a print in
 */
data class GroundMarkRulesSMSG(
  val stampLifetimeMillis: Int,
  val stampRingCapacity: Int,
  val stampBlockIds: List<Int>,
  val wear: Wear,
  val blood: Blood,
) : SMSG {

  /** @property blockWeights block id -> how readily it wears; a block that is absent does not wear at all */
  data class Wear(
    val fadeSeconds: Int,
    val visibleThreshold: Int,
    val stepWeight: Int,
    val neighbourWeight: Int,
    val blockWeights: Map<Int, Double>,
  )

  data class Blood(
    val fadeSeconds: Int,
    val visibleThreshold: Int,
    val spillWeight: Int,
    val spillRadiusTiles: Int,
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val wearRules = GroundMarkRulesSMSGProto.GroundWearRules.newBuilder()
      .setFadeSeconds(wear.fadeSeconds)
      .setVisibleThreshold(wear.visibleThreshold)
      .setStepWeight(wear.stepWeight)
      .setNeighbourWeight(wear.neighbourWeight)

    wear.blockWeights.toSortedMap().forEach { (blockId, weight) ->
      wearRules.addBlockWeights(
        GroundMarkRulesSMSGProto.GroundBlockWeight.newBuilder()
          .setBlockId(blockId)
          .setWeight(weight)
      )
    }

    val bloodRules = GroundMarkRulesSMSGProto.GroundBloodRules.newBuilder()
      .setFadeSeconds(blood.fadeSeconds)
      .setVisibleThreshold(blood.visibleThreshold)
      .setSpillWeight(blood.spillWeight)
      .setSpillRadiusTiles(blood.spillRadiusTiles)

    val rules = GroundMarkRulesSMSGProto.GroundMarkRulesSMSG.newBuilder()
      .setStampLifetimeMillis(stampLifetimeMillis)
      .setStampRingCapacity(stampRingCapacity)
      .addAllStampBlocks(stampBlockIds.sorted())
      .setWear(wearRules)
      .setBlood(bloodRules)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setGroundMarkRules(rules)
      .build()
  }

  companion object {

    /**
     * @param timeSpeedFactor how much faster Bestia time runs than real time, which turns a print's life in
     *   Bestia seconds into the real milliseconds a client ages it by
     */
    fun of(
      stamps: GroundStampConfig,
      wear: GroundWearConfig,
      blood: GroundBloodConfig,
      timeSpeedFactor: Double,
    ): GroundMarkRulesSMSG {
      val lifetimeMillis = (stamps.footprintTtlSeconds * 1_000.0 / timeSpeedFactor).toLong()

      return GroundMarkRulesSMSG(
        stampLifetimeMillis = unsigned32(lifetimeMillis),
        stampRingCapacity = stamps.maxStampsPerColumn,
        stampBlockIds = SurfaceTrampleableGround.CAP_IMPRESSION.keys.map { it.id }.sorted(),
        wear = Wear(
          fadeSeconds = unsigned32(wear.fadeSeconds),
          visibleThreshold = wear.visibleThreshold,
          stepWeight = wear.stepWeight,
          neighbourWeight = wear.neighbourWeight,
          blockWeights = SurfaceTrampleableGround.CAP_WEAR.entries.associate { it.key.id to it.value }.toSortedMap(),
        ),
        blood = Blood(
          fadeSeconds = unsigned32(blood.fadeSeconds),
          visibleThreshold = blood.visibleThreshold,
          spillWeight = blood.spillWeight,
          spillRadiusTiles = blood.spillRadiusTiles,
        ),
      )
    }

    /** The proto's `uint32` fields are Java ints; a setting past 31 bits is clamped rather than sent negative. */
    private fun unsigned32(value: Long): Int {
      return value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
  }
}
