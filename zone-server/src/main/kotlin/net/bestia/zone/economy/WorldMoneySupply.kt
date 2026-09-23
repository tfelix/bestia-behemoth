package net.bestia.zone.economy

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.history.HistoryChannels
import net.bestia.worldgen.pop.EconomyChannels
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.world.WorldRecreatedEvent
import net.bestia.zone.world.WorldService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * The world's coin supply, and each settlement's share of the part of it that starts in NPC hands.
 *
 * A treasury is a slice of a fixed total rather than an amount per resident, and that is what makes the
 * treasuries sum to [npcHoldings] on any seed. Per resident they sum to whatever population the history
 * simulation happened to arrive at, so "NPCs hold half the coin in the world" could not be stated at all.
 */
@Service
class WorldMoneySupply(
  private val worldService: WorldService,
  private val config: EconomyConfig,
) : MoneySupply {

  @Volatile
  private var cachedWeight: Double? = null

  /** Coin across every settlement treasury once they all sit at their reference. */
  val npcHoldings: Double
    get() {
      return config.coinSupply * config.npcShare
    }

  /** Coin the world has still to mint, which is the gold that is in the ground rather than in a purse. */
  val unminted: Double
    get() {
      return config.coinSupply - npcHoldings
    }

  override fun treasuryFor(population: Int, wealth: Double): Double {
    return shareOf(npcHoldings, weightOf(population, wealth), totalWeight())
  }

  private fun totalWeight(): Double {
    cachedWeight?.let { return it }

    synchronized(this) {
      cachedWeight?.let { return it }

      val summed = sumWeights()
      cachedWeight = summed
      LOG.info {
        "Money supply: ${config.coinSupply} coin(s), ${npcHoldings.toLong()} of it across the settlements"
      }

      return summed
    }
  }

  /**
   * Summed over the settlements [SettlementEconomyService.referenceOf] will actually answer for - an
   * economy marker, and somebody living there. A share taken against a wider total would allocate coin
   * to a town no player can ever trade in, and the treasuries would then not sum to [npcHoldings].
   */
  private fun sumWeights(): Double {
    val features = worldService.generated.world.features.all()

    val withEconomy = features
      .filterIsInstance<PointMarker>()
      .filter { it.kind == FeatureKind.SETTLEMENT_ECONOMY }
      .mapNotNullTo(HashSet()) { runCatching { it.attribute(EconomyChannels.INDEX).toInt() }.getOrNull() }

    var total = 0.0
    for (feature in features) {
      if (feature !is PointMarker || feature.kind != FeatureKind.SETTLEMENT_HISTORY) continue

      // A marker missing its channels costs that settlement its share rather than failing the boot, which
      // is `SettlementSiteIndex.build`'s reasoning for a hand-built feature in a test.
      runCatching {
        if (feature.attribute(HistoryChannels.INDEX).toInt() !in withEconomy) return@runCatching

        val population = feature.attribute(HistoryChannels.POPULATION).toInt()
        if (population > 0) total += weightOf(population, feature.attribute(HistoryChannels.WEALTH))
      }
    }

    return total
  }

  @EventListener
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    cachedWeight = null
  }

  companion object {

    /**
     * The distribution key: wealth tilts a town's share without letting a rich hamlet outweigh a city.
     * The shape [PerResidentTreasury] already had, so switching to shares re-proportions nothing.
     */
    fun weightOf(population: Int, wealth: Double): Double {
      return population * (0.5 + wealth)
    }

    /** Zero for a world with nobody in it, where any share would be a division by nothing. */
    fun shareOf(npcHoldings: Double, weight: Double, totalWeight: Double): Double {
      if (totalWeight <= 0.0) return 0.0

      return npcHoldings * weight / totalWeight
    }

    private val LOG = KotlinLogging.logger { }
  }
}
