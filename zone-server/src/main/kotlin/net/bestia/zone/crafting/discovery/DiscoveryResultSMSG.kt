package net.bestia.zone.crafting.discovery

import net.bestia.bnet.proto.DiscoveryResultSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG

/** What a discovery attempt found, sent to the account that tried it. */
data class DiscoveryResultSMSG(
  val outcome: Outcome,

  /** The blueprint learned, or 0 for every other outcome. */
  val recipeId: Long = 0,

  /** Only for [Outcome.NOTHING]: which way the mix is off from the nearest unknown blueprint. */
  val hints: List<AxisHint> = emptyList(),
) : SMSG {

  /** In the order of the proto's `DiscoveryOutcome`. */
  enum class Outcome { NOTHING, LEARNED, SLIPPED, BEYOND_SKILL }

  /** "Too fluid" is [CraftAxis.STATE] with [tooHigh] false: the mix leans to the negative pole. */
  data class AxisHint(val axis: CraftAxis, val tooHigh: Boolean)

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val message = DiscoveryResultSmsgProto.DiscoveryResultSMSG.newBuilder()
      .setOutcomeValue(outcome.ordinal)
      .setRecipeId(recipeId)
      .addAllHints(hints.map {
        DiscoveryResultSmsgProto.AxisHint.newBuilder().setAxis(it.axis.ordinal).setTooHigh(it.tooHigh).build()
      })
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setDiscoveryResult(message)
      .build()
  }
}
