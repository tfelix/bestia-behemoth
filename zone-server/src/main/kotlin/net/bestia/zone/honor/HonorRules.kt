package net.bestia.zone.honor

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.InputStream
import java.time.Duration

/** The numbers of the honor system, read from `honor.yml`. */
class HonorRules(
  private val acts: Map<HonorAct, Amount>,
  val lossFactorWarded: Double,
  val gainFactorWarded: Double,
  val witnessRange: Long,
  val gossip: Gossip,
  val global: HonorLayer,
  val settlement: HonorLayer,
  val grudge: HonorLayer,
  val unfriendlyBelow: Int,
  val hostileBelow: Int,
  val prices: Prices,
) {

  data class Amount(val global: Int, val local: Int)

  data class Prices(
    @JsonProperty("discount-above") val discountAbove: Int,
    @JsonProperty("discount-per-honor") val discountPerHonor: Double,
    @JsonProperty("max-discount") val maxDiscount: Double,
  )

  data class Gossip(
    @JsonProperty("delay-hours") val delayHours: Long,
    val share: Double,
    @JsonProperty("rumour-loss") val rumourLoss: Int,
  ) {
    val delay: Duration
      get() {
        return Duration.ofHours(delayHours)
      }
  }

  fun amountOf(act: HonorAct): Amount {
    return acts.getValue(act)
  }

  private data class File(
    val acts: Map<HonorAct, Amount> = emptyMap(),
    @JsonProperty("protected-zone") val protectedZone: ProtectedZone,
    @JsonProperty("witness-range") val witnessRange: Long,
    val gossip: Gossip,
    val layers: Layers,
    val thresholds: Thresholds,
    val prices: Prices,
  )

  private data class ProtectedZone(
    @JsonProperty("loss-factor") val lossFactor: Double,
    @JsonProperty("gain-factor") val gainFactor: Double,
  )

  private data class Layers(val global: Layer, val settlement: Layer, val grudge: Layer)

  private data class Layer(
    val start: Int,
    val cap: Int,
    @JsonProperty("recovery-per-day") val recoveryPerDay: Int,
    @JsonProperty("grace-days") val graceDays: Long,
    @JsonProperty("permanent-below") val permanentBelow: Int? = null,
  ) {
    fun toLayer(): HonorLayer {
      return HonorLayer(start, cap, recoveryPerDay, Duration.ofDays(graceDays), permanentBelow)
    }
  }

  private data class Thresholds(
    @JsonProperty("unfriendly-below") val unfriendlyBelow: Int,
    @JsonProperty("hostile-below") val hostileBelow: Int,
  )

  companion object {
    const val RESOURCE = "honor.yml"

    /** Refuses a file that leaves an act unpriced, so a new [HonorAct] cannot silently cost nothing. */
    fun read(input: InputStream): HonorRules {
      val file = ObjectMapper(YAMLFactory()).registerKotlinModule().readValue(input, File::class.java)

      val missing = HonorAct.entries - file.acts.keys
      require(missing.isEmpty()) { "$RESOURCE prices no amount for $missing" }

      return HonorRules(
        acts = file.acts,
        lossFactorWarded = file.protectedZone.lossFactor,
        gainFactorWarded = file.protectedZone.gainFactor,
        witnessRange = file.witnessRange,
        gossip = file.gossip,
        global = file.layers.global.toLayer(),
        settlement = file.layers.settlement.toLayer(),
        grudge = file.layers.grudge.toLayer(),
        unfriendlyBelow = file.thresholds.unfriendlyBelow,
        hostileBelow = file.thresholds.hostileBelow,
        prices = file.prices,
      )
    }
  }
}
