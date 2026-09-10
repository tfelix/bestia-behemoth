package net.bestia.zone.economy

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.pop.Sector
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/**
 * The commodities, the trades that make them, and the trades that deliberately make nothing.
 *
 * Behaviour configuration, held in memory like the AI profiles rather than in a table - nothing in the
 * database ever references a commodity by row, because what is stored is a *deviation* keyed on the
 * settlement.
 *
 * The checks here are the ones that need nothing but this file. [EconomyCoverage] holds the rest,
 * because they need catalogues the boot fills in later. I5 - that no commodity is an accumulator
 * nothing drains - is neither: it holds by the shape of the step, and `EconomyStepTest` is what says so.
 */
@Service
class EconomyCatalogue {

  private var byId = LinkedHashMap<String, Commodity>()
  private var trades = emptyList<Trade>()
  private var retail = emptySet<String>()
  private var unbound = emptyList<Unbound>()

  /**
   * Commodities with every input before the good it goes into.
   *
   * The order the step runs in: a day's grain has to be milled before the flour it becomes can be baked,
   * or a shock takes one extra day per stage to reach the shelf. Well defined only because the graph is
   * acyclic, which is I13 and is checked below.
   */
  private var sorted = emptyList<Commodity>()

  val topological: List<Commodity> get() = sorted

  @PostConstruct
  fun load() {
    load(RESOURCE)
  }

  /** The resource is a parameter so a test can hand it a catalogue that breaks one of the checks below. */
  fun load(resource: String) {
    val mapper = JsonMapper.builder(YAMLFactory()).addModule(kotlinModule()).build()
    val file = mapper.readValue(ClassPathResource(resource).inputStream, EconomyYmlDto::class.java)

    byId = LinkedHashMap()
    file.commodities.forEach { dto ->
      require(byId.put(dto.id, dto.toCommodity()) == null) { "Commodity '${dto.id}' is declared twice" }
    }

    trades = file.trades.map { it.toTrade() }
    retail = file.retail.toSet()
    unbound = file.unbound.map { Unbound(it.business, it.needs, it.reason) }

    checkTradesNameKnownCommodities()
    checkEverythingIsSellableSomewhere()
    sorted = sortTopologically()

    LOG.info {
      "Loaded ${byId.size} commodities and ${trades.size} trades; " +
        "${retail.size} retail and ${unbound.size} unbound"
    }
  }

  fun commodity(id: String): Commodity? {
    return byId[id]
  }

  fun commodityOrThrow(id: String): Commodity {
    return commodity(id) ?: throw IllegalArgumentException("Unknown commodity '$id'; known are ${byId.keys}")
  }

  fun commodities(): Collection<Commodity> {
    return byId.values
  }

  fun trades(): List<Trade> {
    return trades
  }

  /** The trade the holder of [business] runs, or null for a business the economy does not model. */
  fun tradeOfBusiness(business: String): Trade? {
    return trades.firstOrNull { it.business == business }
  }

  /** The trade making [commodity], or null for something only trade with the outside world supplies. */
  fun producerOf(commodity: String): Trade? {
    return trades.firstOrNull { it.produces == commodity }
  }

  /** Index into `BusinessCatalogue.ALL` for a trade's business, or -1 for a sector trade. */
  fun businessTypeOf(business: String): Int {
    return BusinessCatalogue.ALL.indexOfFirst { it.id == business }
  }

  fun retailTrades(): Set<String> {
    return retail
  }

  fun unboundTrades(): List<Unbound> {
    return unbound
  }

  /** @param needs an item this trade is waiting for, checked against the item catalogue by [EconomyCoverage] */
  class Unbound(val business: String, val needs: String?, val reason: String)

  private fun checkTradesNameKnownCommodities() {
    for (trade in trades) {
      require(trade.produces in byId) { "Trade '${trade.id}' produces unknown commodity '${trade.produces}'" }
      for (input in trade.consumes) {
        require(input.commodity in byId) {
          "Trade '${trade.id}' consumes unknown commodity '${input.commodity}'"
        }
      }
    }

    val twice = trades.groupBy { it.produces }.filterValues { it.size > 1 }.keys
    require(twice.isEmpty()) {
      "More than one trade produces $twice, so no settlement's reference throughput is well defined"
    }
  }

  /**
   * A commodity whose cover is under the local reserve is never on sale in any town in the world.
   *
   * Two numbers in two files that only interact through a subtraction, which is exactly the shape that
   * goes wrong silently: the good is *there*, the shelves are full, and every shop refuses. Bread is
   * how this was found.
   */
  private fun checkEverythingIsSellableSomewhere() {
    for (commodity in byId.values) {
      require(commodity.coverDays > SettlementMarket.LOCAL_RESERVE_DAYS) {
        "Commodity '${commodity.id}' holds ${commodity.coverDays} days against a " +
          "${SettlementMarket.LOCAL_RESERVE_DAYS}-day local reserve, so no town would ever sell it"
      }
    }
  }

  /**
   * I13: the input to output graph is acyclic.
   *
   * A cycle makes zero an absorbing state - flour needs grain and grain needs flour, so a town that runs
   * out of either can never make more of anything and is permanently dead. Kahn's algorithm, so the
   * failure names the goods still standing in the cycle rather than merely reporting one.
   */
  private fun sortTopologically(): List<Commodity> {
    val inputsOf = byId.keys.associateWith { id ->
      producerOf(id)?.consumes.orEmpty().map { it.commodity }.toMutableSet()
    }
    val sorted = ArrayList<Commodity>(byId.size)
    val ready = ArrayDeque(inputsOf.filterValues { it.isEmpty() }.keys)

    while (ready.isNotEmpty()) {
      val id = ready.removeFirst()
      sorted.add(byId.getValue(id))
      inputsOf.forEach { (other, inputs) ->
        if (inputs.remove(id) && inputs.isEmpty()) ready.add(other)
      }
    }

    require(sorted.size == byId.size) {
      val cycle = byId.keys - sorted.map { it.id }.toSet()
      "The commodity graph has a cycle through $cycle, which makes zero stock an absorbing state"
    }

    return sorted
  }

  private data class EconomyYmlDto(
    val commodities: List<CommodityDto> = emptyList(),
    val trades: List<TradeDto> = emptyList(),
    val retail: List<String> = emptyList(),
    val unbound: List<UnboundDto> = emptyList(),
  )

  private data class CommodityDto(
    val id: String,
    val item: String,
    val price: Double,
    val spoil: Double = 0.0,
    @JsonProperty("cover-days") val coverDays: Double,
    @JsonProperty("per-capita") val perCapita: Double = 0.0,
    val season: SeasonDto? = null,
  ) {
    fun toCommodity() = Commodity(id, item, price, spoil, coverDays, perCapita, season?.toSeason())
  }

  private data class SeasonDto(
    @JsonProperty("peak-day") val peakDay: Int,
    val amplitude: Double,
  ) {
    fun toSeason() = Commodity.Season(peakDay, amplitude)
  }

  private data class TradeDto(
    val id: String,
    val business: String? = null,
    val sector: Sector? = null,
    val building: BuildingFunction,
    val produces: String,
    val consumes: List<InputDto> = emptyList(),
  ) {
    fun toTrade() = Trade(id, business, sector, building, produces, consumes.map { it.toInput() })
  }

  private data class InputDto(
    val commodity: String,
    @JsonProperty("per-unit") val perUnit: Double,
  ) {
    fun toInput() = Trade.Input(commodity, perUnit)
  }

  private data class UnboundDto(
    val business: String,
    val needs: String? = null,
    val reason: String,
  )

  companion object {
    private const val RESOURCE = "economy/economy.yml"

    private val LOG = KotlinLogging.logger { }
  }
}
