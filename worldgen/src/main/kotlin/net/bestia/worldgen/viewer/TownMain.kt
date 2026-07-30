package net.bestia.worldgen.viewer

import net.bestia.worldgen.civ.BuildingChannels
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.SettlementRecord
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.history.HistoryChannels
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.pop.BusinessChannels
import net.bestia.worldgen.pop.EconomyChannels
import net.bestia.worldgen.pop.Households
import net.bestia.worldgen.pop.EconomyProbe
import net.bestia.worldgen.pop.Sector
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Vec2d
import java.util.Locale

/**
 * Inspects one settlement: its layout, its economy, and what happened to it.
 *
 * ### The scale nothing else could show
 *
 * The viewer renders the whole world into a few hundred pixels, so a town is one pixel. The probe prints a
 * 48 metre window, so a town is four hundred probes. A town is two hundred to fifteen hundred metres across,
 * which is exactly the gap between the two tools - and the *reason* the gap mattered is that everything step
 * 8 produces lives in it. A street network that came out as a tree with no blocks in it looks identical to a
 * correct one at world scale and identical to open ground at voxel scale.
 *
 * So this renders one settlement at about a metre per pixel, from the materialised voxel surface rather than
 * from the plan, on the same argument the seam check rests on: what is drawn is what the client will get, so
 * the view cannot quietly agree with a layout the chunks disagree with.
 *
 * ```
 * ./gradlew :worldgen:town                          # the largest settlement in the world
 * ./gradlew :worldgen:town -Pnth=3                  # the fourth largest
 * ./gradlew :worldgen:town -Pindex=17               # a particular settlement index
 * ./gradlew :worldgen:town -Ptier=village           # the largest of a tier
 * ./gradlew :worldgen:town -Pruin                   # a settlement history destroyed
 * ./gradlew :worldgen:town -Pwhy                    # ...and why every trade is or is not there
 * ./gradlew :worldgen:town -Pout=build/town.png     # write the map somewhere else
 * ./gradlew :worldgen:town -Pcensus                 # every settlement in one table instead
 * ```
 */
object TownMain {

  @JvmStatic
  fun main(args: Array<String>) {
    val cli = WorldArgs(args.toList(), extraFlags = TOWN_FLAGS)
    val config = cli.worldConfig(
      StandardWorld.demoConfig().copy(widthCells = 192, heightCells = 192)
    )

    println("world ${WorldArgs.summary(config)}")
    val generated = StandardWorld.build(config)
    val view = TownView(generated)

    if (cli.has("--census")) {
      view.census()
      return
    }

    val chosen = view.choose(
      index = cli.int("--index"),
      nth = cli.int("--nth") ?: 0,
      tier = cli.value("--tier"),
      ruin = cli.has("--ruin")
    )
    if (chosen == null) {
      println("no settlement matched; try --census to see what this world has")
      return
    }

    view.describe(chosen)
    view.layout(chosen)
    view.economy(chosen, why = cli.has("--why"))
    view.timeline(chosen)
    view.households(chosen)

    val out = cli.value("--out") ?: "build/town.png"
    view.render(chosen, out, cli.double("--metres-per-pixel") ?: 1.0)
  }

  /** One settlement, with everything the tool needs already gathered. */
  private class Place(
    val index: Int,
    val position: Vec2d,
    val tier: SettlementTier,
    val culture: Culture,
    val record: SettlementRecord,
    val buildings: List<FootprintFeature>,
    val businesses: List<PointMarker>,
    val economy: PointMarker?,
    val streets: Int,
    val wallStretches: Int,
    val gates: Int,
    val builtRadius: Double
  ) {
    val name: String get() = Names.place(record.nameSeed, Culture.indexOf(culture))
    val standing: Boolean get() = record.wasFounded && !record.isRuin
  }

  private class TownView(private val generated: GeneratedWorld) {

    private val config: WorldConfig get() = generated.config
    private val chronicle: Chronicle get() = generated.world.chronicle
    private val features = generated.world.features.all()

    private val places: List<Place> = build()

    private fun build(): List<Place> {
      val sites = features.filter { it.kind == FeatureKind.SETTLEMENT }
        .filterIsInstance<PointMarker>()
        .associateBy { it.attribute(SettlementChannels.INDEX).toInt() }

      val buildings = features.filter { it.kind == FeatureKind.BUILDING }
        .filterIsInstance<FootprintFeature>()
        .groupBy { it.attribute(BuildingChannels.SETTLEMENT).toInt() }

      val businesses = features.filter { it.kind == FeatureKind.BUSINESS }
        .filterIsInstance<PointMarker>()
        .groupBy { it.attribute(BusinessChannels.SETTLEMENT).toInt() }

      val economies = features.filter { it.kind == FeatureKind.SETTLEMENT_ECONOMY }
        .filterIsInstance<PointMarker>()
        .associateBy { it.attribute(EconomyChannels.INDEX).toInt() }

      return chronicle.settlements.mapNotNull { record ->
        val site = sites[record.index] ?: return@mapNotNull null
        val radius = radiusOf(record, site)

        Place(
          index = record.index,
          position = site.position,
          tier = SettlementTier.entries[site.attribute(SettlementChannels.TIER).toInt()],
          culture = Culture.byIndex(site.attribute(SettlementChannels.CULTURE).toInt()),
          record = record,
          buildings = buildings[record.index].orEmpty(),
          businesses = businesses[record.index].orEmpty(),
          economy = economies[record.index],
          // Streets carry no settlement channel, so they are counted by falling inside the town's own
          // extent. Adequate for a count and honest about what it is: two settlements closer together than
          // their radii would share the tally, which at the tier separations here does not happen.
          streets = countNear(FeatureKind.STREET, site.position, radius * 1.3),
          wallStretches = countNear(FeatureKind.TOWN_WALL, site.position, radius * 1.5),
          gates = countNear(FeatureKind.GATE, site.position, radius * 1.5),
          builtRadius = radius
        )
      }
    }

    private fun radiusOf(record: SettlementRecord, site: PointMarker): Double {
      // A ruin has no population, so its built radius is meaningless - what a reader wants to see is the
      // rubble field, and its extent is on the site. Without this the map of a razed city was eighty pixels
      // across and showed grass.
      if (record.isRuin) {
        record.sites.map { chronicle.sites[it] }
          .firstOrNull { it.kind == net.bestia.worldgen.core.SiteKind.RUIN }
          ?.let { return it.radius }
      }

      val tier = SettlementTier.entries[site.attribute(SettlementChannels.TIER).toInt()]
      val hectares = record.population / TOWN_PEOPLE_PER_HECTARE
      return minOf(
        Math.sqrt(maxOf(hectares, 0.05) * 10_000.0 / Math.PI),
        tier.footprintRadius * 0.95
      ).coerceAtLeast(30.0)
    }

    private fun countNear(kind: FeatureKind, at: Vec2d, radius: Double): Int =
      features.count { it.kind == kind && at.distanceTo(Vec2d(it.bbox.centerX, it.bbox.centerY)) < radius }

    fun choose(index: Int?, nth: Int, tier: String?, ruin: Boolean): Place? {
      if (index != null) return places.firstOrNull { it.index == index }

      val candidates = places
        .filter { if (ruin) it.record.isRuin else it.standing }
        .filter { tier == null || it.tier.label.equals(tier, ignoreCase = true) }
        .sortedWith(compareByDescending<Place> { it.record.population }.thenBy { it.index })

      return candidates.getOrNull(nth)
    }

    // --- Text views -----------------------------------------------------------------------------------

    fun describe(place: Place) {
      val record = place.record
      println()
      println("=== ${place.name} ===")
      line("index", "${place.index}")
      line("at", "(${place.position.x.toInt()}, ${place.position.y.toInt()})")
      line("tier / culture", "${place.tier.label} / ${place.culture.name} (${place.culture.layout})")
      line("founded", if (record.wasFounded) "year ${record.foundedYear}" else "never settled")
      if (record.isRuin) {
        line("abandoned", "year ${record.abandonedYear} - ${record.ruinCause?.name?.lowercase()}")
      }
      line("population", "${record.population}")
      line("wealth", fixed(record.wealth))
      line("sacked", "${record.timesSacked}")
      line(
        "walls",
        if (record.wallYear == 0) "none" else "built year ${record.wallYear} around ${record.wallPopulation} people"
      )
      chronicle.civOf(place.index)?.let {
        line(
          "held by",
          "${Names.civ(it.nameSeed, it.cultureIndex)}, technology ${fixed(it.technology)}"
        )
      }
      if (record.oldNameSeed != 0L) {
        line("formerly", Names.place(record.oldNameSeed, Culture.indexOf(place.culture)))
      }
    }

    /**
     * What the layout produced, and - the number this tool exists for - what it wanted to produce.
     *
     * A shortfall between wanted and built is the single most useful diagnostic in step 8, because the two
     * causes look identical on a map: either the street graph produced too few blocks to hold the buildings,
     * or the per-settlement cap bound. Printing both makes them one glance apart.
     */
    fun layout(place: Place) {
      val wanted = maxOf(1, (place.record.population / TOWN_PEOPLE_PER_BUILDING).toInt())
      val built = place.buildings.size

      println()
      println("layout")
      line("built radius", "${place.builtRadius.toInt()} m")
      line("streets", "${place.streets} chains")
      line("wall", "${place.wallStretches} stretches, ${place.gates} gates")
      line(
        "buildings", "$built built, $wanted wanted" + when {
          built >= wanted -> ""
          built >= TOWN_BUILDING_CAP -> " - the per-settlement cap bound"
          else -> " - the street layout had room for only $built plots"
        }
      )

      if (built == 0) return

      val byFunction = place.buildings.groupingBy {
        BuildingFunction.entries[it.attribute(BuildingChannels.FUNCTION).toInt()]
      }.eachCount()

      println()
      println("buildings by function")
      byFunction.entries.sortedByDescending { it.value }.forEach { (function, count) ->
        line("  ${function.label}", "$count")
      }

      val storeys = place.buildings.map { it.attribute(BuildingChannels.STOREYS).toInt() }
      val stone = place.buildings.count {
        it.attribute(BuildingChannels.WALL_BLOCK).toInt() == net.bestia.worldgen.voxel.BlockType.MASONRY.id
      }
      println()
      line("mean storeys", fixed(storeys.average()))
      line("stone walled", "$stone of $built (${percent(stone, built)})")
    }

    /**
     * The economy, and on request the full precondition trace.
     *
     * The trace is the answer to the question step 9 is most often asked - "why does this mining town have
     * no baker" - and it prints the *evidence*, not just the verdict, because "needs arable land producing
     * cereal" is an assertion and "cereal per resident 0.11, needs 0.35" is a diagnosis.
     */
    fun economy(place: Place, why: Boolean) {
      val economy = place.economy
      if (economy == null) {
        println()
        println("economy: none - this settlement has no economy marker")
        return
      }

      println()
      println("economy")
      line("food capacity", "${economy.attribute(EconomyChannels.FOOD_CAPACITY).toInt()} residents")
      line("food surplus", "${economy.attribute(EconomyChannels.FOOD_SURPLUS).toInt()} residents")
      line("cereal share", fixed(economy.attribute(EconomyChannels.CEREAL_SHARE)))
      line("pasture", fixed(economy.attribute(EconomyChannels.PASTURE)))
      line("road traffic", fixed(economy.attribute(EconomyChannels.TRAFFIC)))
      line("households", "${economy.attribute(EconomyChannels.HOUSEHOLD_COUNT).toInt()}")

      println()
      println("employment")
      val sectors = listOf(
        Sector.FARM to EconomyChannels.FARMERS,
        Sector.CRAFT to EconomyChannels.CRAFTERS,
        Sector.TRADE to EconomyChannels.TRADERS,
        Sector.SERVICE to EconomyChannels.SERVANTS,
        Sector.ADMIN to EconomyChannels.ADMINISTRATORS,
        Sector.CLERGY to EconomyChannels.CLERGY,
        Sector.MILITARY to EconomyChannels.SOLDIERS
      )
      val total = sectors.sumOf { economy.attribute(it.second).toInt() }
      for ((sector, channel) in sectors) {
        val count = economy.attribute(channel).toInt()
        if (count == 0) continue
        line("  ${sector.name.lowercase()}", "$count (${percent(count, total)})")
      }

      println()
      println("businesses")
      if (place.businesses.isEmpty()) {
        println("  none placed")
      } else {
        place.businesses
          .groupingBy { BusinessCatalogue.ALL[it.attribute(BusinessChannels.TYPE).toInt()].label }
          .eachCount()
          .entries.sortedByDescending { it.value }
          .forEach { (label, count) -> line("  $label", "$count") }
      }

      if (!why) {
        println()
        println("  (pass -Pwhy for the precondition trace behind every trade)")
        return
      }

      val decisions = BusinessCatalogue.evaluate(EconomyProbe.settingFor(generated, place.index) ?: return)
      println()
      println("why - every trade in the catalogue, and what decided it")
      for (decision in decisions) {
        val mark = if (decision.exists) "%2d".format(decision.count) else " -"
        println("  $mark  ${decision.type.label.padEnd(16)} ${decision.reason}")
      }
    }

    /** Everything the chronicle logged about this place, in order. The timeline view. */
    fun timeline(place: Place) {
      val events = chronicle.eventsOf(Actor(ActorType.SETTLEMENT, place.index))
      println()
      println("timeline (${events.size} logged events)")
      if (events.isEmpty()) {
        println("  nothing was ever recorded here")
        return
      }
      events.forEach { println("  ${it.year.toString().padStart(5)}  ${it.detail}") }

      val sites = place.record.sites.map { chronicle.sites[it] }
      if (sites.isNotEmpty()) {
        println()
        println("sites")
        sites.forEach {
          val artifact = it.artifact.takeIf { a -> a >= 0 }?.let { a ->
            val relic = chronicle.artifacts[a]
            ", holding ${Names.artifact(relic.nameSeed, 0, relic.kind, relic.forgedAtNameSeed)}"
          } ?: ""
          println(
            "  ${it.kind.name.lowercase().padEnd(11)} year ${it.year}, " +
                "${it.radius.toInt()} m, decay ${fixed(it.decay)}$artifact"
          )
        }
      }
    }

    /**
     * A sample of the households the summary expands to.
     *
     * Printed because it is the only way to see that the LOD expansion is producing people rather than
     * placeholders - a town of four hundred households whose heads are all thirty-one years old with no
     * children is a plausible-looking table and a broken demographic pyramid.
     */
    fun households(place: Place) {
      val summary = EconomyProbe.summaryFor(generated, place.index) ?: return
      if (summary.householdCount == 0) return

      println()
      println("households, ${summary.householdCount} of them - a sample")
      val step = maxOf(1, summary.householdCount / HOUSEHOLD_SAMPLE)
      var shown = 0
      var index = 0
      while (index < summary.householdCount && shown < HOUSEHOLD_SAMPLE) {
        val household = Households.one(summary, index)
        val trade = household.business.takeIf { it >= 0 }
          ?.let { BusinessCatalogue.ALL[it].label } ?: "farmer"
        val ages = household.members.joinToString(",") { "${it.kinship.name.take(2).lowercase()}${it.age}" }
        println(
          "  #${index.toString().padEnd(5)} ${trade.padEnd(16)} " +
              "wealth ${fixed(household.wealth)}  ${household.size} people: $ages"
        )
        index += step
        shown++
      }

      val all = Households.expand(summary)
      val people = all.sumOf { it.size }
      val children = all.sumOf { h -> h.members.count { it.age < 15 } }
      println()
      line("expanded people", "$people against a population of ${place.record.population}")
      line("under fifteen", "$children (${percent(children, people)})")
      line(
        "mean household",
        fixed(people.toDouble() / all.size) + " people"
      )

      val graph = Households.socialGraph(summary)
      line("social graph", "mean degree ${fixed(graph.sumOf { it.size }.toDouble() / graph.size)}")
      val known = Households.knowledgeOf(summary, all.first(), chronicle)
      line("household 0 knows", "${known.size} of ${chronicle.events.size} logged events")
    }

    /** Every settlement in one table. What to open first when a world's towns look wrong in aggregate. */
    fun census() {
      println()
      println("${places.size} settlement sites")
      println(
        "  idx  tier     pop     wealth  found  sack  wall  bldg  biz   name"
      )
      for (place in places.sortedWith(compareByDescending { it.record.population })) {
        val record = place.record
        println(
          "  %4d  %-7s %6d  %6s  %5s  %4d  %4s  %4d  %4d  %s%s".format(
            Locale.ROOT,
            place.index,
            place.tier.label,
            record.population,
            fixed(record.wealth),
            if (record.wasFounded) record.foundedYear.toString() else "-",
            record.timesSacked,
            if (record.wallYear == 0) "-" else record.wallYear.toString(),
            place.buildings.size,
            place.businesses.size,
            place.name,
            if (record.isRuin) " (ruin, ${record.abandonedYear})" else ""
          )
        )
      }

      val standing = places.count { it.standing }
      val ruins = places.count { it.record.isRuin }
      val unsettled = places.count { !it.record.wasFounded }
      println()
      line("standing", "$standing")
      line("ruins", "$ruins")
      line("never settled", "$unsettled")
      line("buildings", "${places.sumOf { it.buildings.size }}")
      line("businesses", "${places.sumOf { it.businesses.size }}")
      line("walled", "${places.count { it.record.wallYear != 0 }}")

      val missing = places.filter { it.standing && it.buildings.isEmpty() }
      if (missing.isNotEmpty()) {
        println()
        println(
          "${missing.size} standing settlements have no buildings at all: " +
              missing.take(8).joinToString(", ") { "${it.index} (${it.record.population})" }
        )
      }
    }

    // --- The map --------------------------------------------------------------------------------------

    /**
     * Renders the settlement from the materialised voxel surface, one pixel per voxel.
     *
     * From the voxels rather than from the features on purpose, and it is the same argument the chunk seam
     * check rests on: a view drawn from the plan agrees with the plan by construction and would show a
     * correct town whose chunks contain nothing. What is drawn here is what the client gets.
     */
    fun render(place: Place, path: String, metresPerPixel: Double) {
      val span = place.builtRadius * MAP_MARGIN
      val pixels = ((span * 2.0) / metresPerPixel).toInt().coerceIn(64, MAX_MAP_PIXELS)

      val view = Viewport(
        centerX = place.position.x,
        centerY = place.position.y,
        metresPerPixel = metresPerPixel,
        widthPx = pixels,
        heightPx = pixels
      )

      val field = ChunkSurfaceField(config, generated.materializer)
      // The interactive viewer's chunk budget exists to keep panning responsive, and refusing the view is the
      // right answer there. Here it is not: materialising a town is the entire job, so the budget is raised to
      // cover it. Left at its default the first version wrote a two-thousand-pixel image of nothing and said
      // the map was written, which is exactly the failure the surface views were fixed for once already.
      val chunksAcross = (view.bounds.width / config.chunkExtent + 2.0).toInt()
      field.chunkBudget = maxOf(field.chunkBudget, chunksAcross * chunksAcross)

      val rendered = MapRenderer(config).render(
        field = field,
        view = view,
        options = RenderOptions(hillshade = false, features = true, chunkGrid = false),
        features = generated.world.features.query(view.bounds)
      )

      val file = java.io.File(path)
      file.parentFile?.mkdirs()
      javax.imageio.ImageIO.write(rendered.image, "png", file)

      println()
      println("map: ${file.absolutePath} - ${pixels}x$pixels at $metresPerPixel m/px")
      // A view that could not be evaluated must say so rather than let a blank image speak for it.
      rendered.unavailable?.let {
        println("  NOT RENDERED: $it")
        return
      }
      println("  surface material, with the vector features drawn over it")
    }

    private fun line(label: String, value: String) = println("  ${label.padEnd(18)} $value")

    private fun fixed(value: Double) = "%.2f".format(Locale.ROOT, value)

    private fun percent(n: Int, total: Int) =
      if (total == 0) "0%" else "%.1f%%".format(Locale.ROOT, 100.0 * n / total)
  }

  /** Households sampled for the text view. Enough to see a spread, few enough to read. */
  private const val HOUSEHOLD_SAMPLE = 10

  /** Must match `TownParams.peoplePerHectare` and `peoplePerBuilding`; see [TownView.radiusOf]. */
  private const val TOWN_PEOPLE_PER_HECTARE = 85.0
  private const val TOWN_PEOPLE_PER_BUILDING = 5.5
  private const val TOWN_BUILDING_CAP = 1_200

  /** How much beyond the built radius the map shows, so the edge of town is visible. */
  private const val MAP_MARGIN = 1.35

  private const val MAX_MAP_PIXELS = 2_400

  private val TOWN_FLAGS = setOf(
    "--index", "--nth", "--tier", "--ruin", "--why", "--census", "--out", "--metres-per-pixel"
  )
}
