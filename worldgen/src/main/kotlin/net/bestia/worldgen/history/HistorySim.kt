package net.bestia.worldgen.history

import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.ArtifactKind
import net.bestia.worldgen.core.ArtifactRecord
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.CivRecord
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.FigureRecord
import net.bestia.worldgen.core.FigureRole
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.HistoryEvent
import net.bestia.worldgen.core.SettlementRecord
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.SiteRecord
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.max
import kotlin.math.min

/**
 * Everything the simulation needs to know about one settlement site, read off the world tier once.
 *
 * Read once and frozen, rather than sampled from layers inside the year loop: the loop runs a couple of
 * hundred times over every site, and a bilinear sample per site per tick would make the stage's cost a
 * function of how long history is rather than of how many places there are.
 */
class SiteFacts(
  val index: Int,
  val position: Vec2d,
  val tier: SettlementTier,
  val cultureIndex: Int,
  /**
   * The population placement decided this site could support. The simulation treats it as the *ceiling*
   * at present-day technology rather than as the answer, and grows towards it.
   */
  val potential: Int,
  val habitability: Double,
  val fertility: Double,
  /** 0 to 1: how close to a convergent plate boundary, hence how likely to be buried in ash. */
  val volcanism: Double,
  /** 0 to 1: deep recent sediment close to a big river. Where the crops are and where the flood is. */
  val floodRisk: Double,
  val coastal: Boolean,
  val resourceValue: Double
) {
  val culture: Culture get() = Culture.byIndex(cultureIndex)
}

/** Tuning for [HistoryStage]. */
data class HistoryParams(

  /** Years simulated before play begins. A thousand at five-year steps is two hundred ticks. */
  val years: Int = 1000,

  /**
   * Years per simulation tick.
   *
   * Five, as the architecture document suggests. The granularity that matters is not the step but the
   * *entity*: this simulates settlements and a few hundred notable figures, never individuals, which is
   * what keeps a thousand years of a few hundred places to a fraction of a second.
   */
  val yearsPerTick: Int = 5,

  /** Annual population growth at low occupancy, before the logistic term bites. */
  val growthRate: Double = 0.007,

  /** Technology gained per year at unit inventiveness. Over the default span this reaches about 0.9. */
  val technologyRate: Double = 0.0009,

  /** Habitability below which a civ will not bother founding anything, even with people to spare. */
  val minFoundingHabitability: Double = 0.32,

  /**
   * Occupancy at which a settlement starts sending out daughter settlements.
   *
   * Per settlement, not per civ, and that is the fix for a bug worth recording. Tested against the civ's
   * *aggregate* occupancy, founding a new settlement lowers the aggregate - the daughter starts nearly empty -
   * so a civ that crossed the threshold immediately fell back below it and had to regrow for a century before
   * it could found another. One world came out with a single city and twenty-seven sites nobody had ever
   * settled, which the `town -Pcensus` view showed in one line and which looked, on the map, like a sparsely
   * populated world rather than a broken one.
   */
  val expansionPressure: Double = 0.35,

  /**
   * Chance per tick that a settlement at full occupancy founds a daughter.
   *
   * A chance rather than a certainty so that foundings spread over centuries instead of all landing in the
   * decade the threshold was crossed - which would give every settlement in the world the same founding date
   * and leave the chronicle with nothing to say about nine hundred of its thousand years.
   */
  val foundingChance: Double = 0.30,

  /** Most settlements one civ may found in one tick, so a crowded civ does not fill the map at once. */
  val foundingsPerTick: Int = 2,

  /** Hostility gained per tick between neighbours at unit bellicosity and zero distance. */
  val hostilityRate: Double = 0.028,

  /** Hostility at which war is declared. */
  val warThreshold: Double = 1.0,

  /** Metres beyond which two civs never come into contact at all. */
  val contactRange: Double = 220_000.0,

  /** A war lasts between these many years, unless a razing ends it early. */
  val minWarYears: Int = 10,
  val maxWarYears: Int = 45,

  /** Population above which a threatened settlement can afford walls. */
  val wallPopulation: Int = 900,

  // --- The four built sites -------------------------------------------------------------------------
  //
  // Every threshold below is a gate on *where* a site may go, and is read by SpecialSiteCandidates rather
  // than by the simulation. What the simulation decides is whether a civ ever gets round to building one.

  /** Ore concentration below which a deposit is not worth sinking a shaft into. */
  val mineRichness: Double = 0.45,

  /** Metres below the surface a pre-industrial civilisation will follow a seam. */
  val mineDepth: Double = 120.0,

  /** Metres from a deposit within which a settlement could have found and worked it. */
  val mineRange: Double = 30_000.0,

  /** Metres a monastery keeps between itself and the nearest settlement. Its defining property. */
  val monasteryClearance: Double = 22_000.0,

  /** A fort sits outside a town but on ground somebody travels through. */
  val fortClearance: Double = 8_000.0,
  val fortRange: Double = 45_000.0,

  /** Metres either side of a candidate that the saddle test compares against. */
  val saddleSpan: Double = 3_000.0,

  /** Metres of relief a saddle needs, both up to its shoulders and down through its gap. */
  val saddleRelief: Double = 110.0,

  /** Metres from open water within which a headland can carry a light that is any use. */
  val lighthouseRange: Double = 3_500.0,

  /** A light inside a town is a lamp; this is what makes it a landmark on the approach. */
  val lighthouseClearance: Double = 5_000.0,

  /**
   * Metres of ground above sea level a built site needs under it. See `SpecialSites.isDryGround`.
   *
   * Not zero, because the elevation raster is a kilometre grid and these structures are tens of metres across:
   * a centre that is barely above the water has a footprint the chunk tier's detail noise puts partly under it.
   */
  val siteFreeboard: Double = 14.0,

  /** The same for a lighthouse, which belongs on the rocks and would be moved inland by the figure above. */
  val lighthouseFreeboard: Double = 5.0,

  /** Metres between two candidates of the same kind, so a scan does not return one hilltop nine times. */
  val siteSeparation: Double = 12_000.0,

  /** Sampling stride for the terrain scans, in metres. These are all kilometre-scale landforms. */
  val candidateStride: Double = 4_000.0,

  /** Candidates kept per kind. The simulation founds at most one site per civ per pass, so this is ample. */
  val maxCandidates: Int = 48,

  /** Technology a civ needs before it builds each kind. A lighthouse is easier than a deep mine. */
  val mineTechnology: Double = 0.35,
  val monasteryTechnology: Double = 0.25,
  val fortTechnology: Double = 0.20,
  val lighthouseTechnology: Double = 0.45,

  /** Chance per civ per tick that a civ with the means and the reason actually builds one. */
  val builtSiteChance: Double = 0.06,

  /** Years between two sites of the same kind founded by one civ, so they arrive across a history. */
  val builtSiteInterval: Int = 120,

  /** Years between notable figures in a civ, roughly. */
  val yearsPerFigure: Int = 35,

  val figureLifespan: Int = 62,

  /**
   * Importance below which an event is only sampled rather than kept.
   *
   * The log has to be prunable or it is unbounded: a thousand years of a few hundred settlements
   * generates a growth event per settlement per tick, which is tens of thousands of lines saying a town
   * got slightly bigger. Everything at or above this is kept forever; below it, one in
   * [minorEventSampling] survives, so the texture is still there and the volume is not.
   */
  val importanceFloor: Int = 40,

  val minorEventSampling: Int = 24
)

/**
 * Step 10: the coarse-grained agent simulation that gives the world a past.
 *
 * ### What it simulates, and what it deliberately does not
 *
 * Civilisations, settlements, wars, disasters, a few hundred notable figures, and the artifacts they
 * make. At settlement granularity and five-year steps, which is the granularity the architecture document
 * argues for and the reason this costs milliseconds rather than minutes.
 *
 * It does **not** place settlements. Those already exist - [net.bestia.worldgen.civ.SettlementStage] put
 * them where the land is good - and history's job is to say *when* each was founded, who holds it, how
 * often it burned, and which of them are no longer there. That inversion is what the build order means by
 * "retrofit ruins, artifacts and grudges into the existing world", and it avoids the alternative, in which
 * history founds settlements and immediately has to re-derive every habitability term to decide where.
 *
 * The consequence to know about: a site that history never founded, or destroyed, still has a
 * `SETTLEMENT` marker from placement. That marker means "somebody would settle here", not "somebody
 * does". The `SETTLEMENT_HISTORY` marker beside it is what says which.
 *
 * ### Determinism
 *
 * Every random decision goes through [roll], which hashes the world seed together with the year, the
 * subject and a salt naming the decision. Nothing draws from a stream, and that is not a stylistic
 * choice: a stream makes the result depend on how many draws happened earlier, so adding one disaster
 * type would silently rewrite every war in the world. Keyed rolls make each decision independent of every
 * other, so a new decision is additive.
 */
internal class HistorySim(
  private val params: HistoryParams,
  private val facts: List<SiteFacts>,
  /** Where the four built sites *may* go, read off the terrain by the stage. See [SpecialSiteCandidates]. */
  private val candidates: SpecialSiteCandidates,
  /** Base for every keyed roll: the world seed folded with the stage's identity and version. */
  private val streamBase: Long,
  private val worldSeed: Long
) {

  private val towns = facts.map { Town(it) }
  private val civs = ArrayList<Civ>()
  private val people = ArrayList<Person>()
  private val relics = ArrayList<Relic>()
  private val sites = ArrayList<Site>()
  private val wars = ArrayList<War>()

  private val events = ArrayList<HistoryEvent>()
  private var prunedEvents = 0
  private var nextEventId = 0

  /** Pairwise hostility, `[a][b]`, symmetric. Grows with contact and resets when war is declared. */
  private lateinit var hostility: Array<DoubleArray>

  /** Straight-line distances between every pair of sites, in metres. */
  private val distance: Array<DoubleArray> = Array(facts.size) { a ->
    DoubleArray(facts.size) { b -> facts[a].position.distanceTo(facts[b].position) }
  }

  private val startYear = 1
  private val presentYear = params.years

  fun run(): Chronicle {
    seedCivilisations()
    hostility = Array(civs.size) { DoubleArray(civs.size) }

    var year = startYear
    while (year <= presentYear) {
      for (civ in civs) {
        if (!civ.exists) continue
        growPopulations(civ, year)
        expandOrMigrate(civ, year)
        advanceTechnology(civ, year)
      }
      updateRelations(year)
      resolveWars(year)
      resolveDisasters(year)
      updateFigures(year)
      updateArtifacts(year)
      raiseMonuments(year)
      buildSites(year)
      buildWalls(year)
      retireCivs(year)

      year += params.yearsPerTick
    }

    return assemble()
  }

  // --- Setup -----------------------------------------------------------------------------------------

  /**
   * Cities seed civilisations; a city near an existing capital of its own culture joins it instead.
   *
   * The rule is deliberately geographic rather than a target count. "One civ per N cities" would put two
   * unrelated peoples on the same island and split a single culture across an ocean; "join the nearest
   * capital of your culture if it is within reach" produces civs whose extent is the extent their culture
   * can actually hold, which is what [Culture.expansionRange] means.
   */
  private fun seedCivilisations() {
    val cities = facts.filter { it.tier == SettlementTier.CITY }

    for (site in cities) {
      val town = towns[site.index]
      val culture = site.culture

      val host = civs
        .filter { it.cultureIndex == site.cultureIndex }
        .filter { distance[site.index][it.capital] <= culture.expansionRange }
        .minByOrNull { distance[site.index][it.capital] }

      if (host != null) {
        joinCiv(host, town, startYear)
        continue
      }

      val civ = Civ(civs.size, site.cultureIndex, site.index)
      civ.nameSeed = Names.seedOf(worldSeed, CIV_NAME_SALT, civ.index.toLong())
      civ.founded = startYear
      civs.add(civ)
      joinCiv(civ, town, startYear)

      log(
        startYear, EventKind.CIV_FOUNDED,
        listOf(Actor(ActorType.CIV, civ.index), Actor(ActorType.SETTLEMENT, site.index)),
        site.position, emptyList(),
        "the ${Names.civ(civ.nameSeed, civ.cultureIndex)} settle at ${nameOf(town)}"
      )
    }

    // A world too small for a city gets one civ anyway, seeded on its best site - otherwise nothing has a
    // history at all, which is a worse answer than a small one.
    if (civs.isEmpty() && facts.isNotEmpty()) {
      val site = facts.first()
      val civ = Civ(0, site.cultureIndex, site.index)
      civ.nameSeed = Names.seedOf(worldSeed, CIV_NAME_SALT, 0L)
      civ.founded = startYear
      civs.add(civ)
      joinCiv(civ, towns[site.index], startYear)
    }
  }

  private fun joinCiv(civ: Civ, town: Town, year: Int) {
    town.founded = year
    town.owner = civ.index
    town.foundingCiv = civ.index
    town.population = max(1.0, town.facts.potential * INITIAL_OCCUPANCY)
    town.wealth = (0.25 + town.facts.resourceValue * 0.35 + town.facts.habitability * 0.3)
      .coerceIn(0.0, 1.0)
    town.nameSeed = Names.seedOf(worldSeed, PLACE_NAME_SALT, town.facts.index.toLong())
    civ.towns.add(town.facts.index)
  }

  // --- Population and expansion ----------------------------------------------------------------------

  /** Logistic growth towards a carrying capacity that technology raises. */
  private fun growPopulations(civ: Civ, year: Int) {
    // Over a snapshot: a settlement that dwindles to nothing is abandoned inside this loop, and
    // abandonment removes it from the civ's own list.
    for (index in civ.towns.toList()) {
      val town = towns[index]
      if (!town.standing) continue

      val capacity = capacityOf(town, civ)
      val rate = params.growthRate * params.yearsPerTick
      town.population += rate * town.population * (1.0 - town.population / capacity)
      town.population = town.population.coerceIn(0.0, capacity * 1.05)

      // Wealth follows population and trade, with the resource base as a floor - a mining town is rich
      // for a mining town's reasons even when it is small.
      val target = (0.2 + 0.5 * (town.population / max(1.0, capacity)) +
          0.3 * town.facts.resourceValue).coerceIn(0.0, 1.0)
      town.wealth += (target - town.wealth) * 0.1

      if (town.population < 12.0 && town.founded < year - 50) {
        abandon(town, year, EventKind.SETTLEMENT_ABANDONED, null, "dwindled to nothing")
      }
    }

    civ.peak = max(civ.peak, civ.towns.sumOf { towns[it].population.toInt() })
  }

  private fun capacityOf(town: Town, civ: Civ): Double =
    max(20.0, town.facts.potential * (0.5 + 0.5 * civ.technology) * town.decline)

  /**
   * Crowded settlements send out daughter settlements.
   *
   * Per settlement rather than per civ - see [HistoryParams.expansionPressure] for the bug that taught that -
   * and probabilistically, so foundings spread across the simulated span. The daughter goes to the best
   * unsettled site the *parent* can reach, which is what makes a civ's extent grow outwards from its capital
   * rather than teleport to whichever unclaimed site scored highest anywhere.
   */
  private fun expandOrMigrate(civ: Civ, year: Int) {
    var founded = 0

    for (index in civ.towns.toList()) {
      if (founded >= params.foundingsPerTick) return

      val parent = towns[index]
      if (!parent.standing) continue

      val occupancy = parent.population / capacityOf(parent, civ)
      if (occupancy < params.expansionPressure) continue

      val crowding = ((occupancy - params.expansionPressure) / (1.0 - params.expansionPressure))
        .coerceIn(0.0, 1.0)
      if (roll(year.toLong(), index.toLong(), EXPAND_SALT) >= crowding * params.foundingChance) continue

      val target = bestSiteNear(parent, civ, year) ?: continue
      val town = towns[target]
      joinCiv(civ, town, year)
      founded++

      log(
        year, EventKind.SETTLEMENT_FOUNDED,
        listOf(Actor(ActorType.SETTLEMENT, target), Actor(ActorType.CIV, civ.index)),
        town.facts.position, emptyList(),
        "${nameOf(town)} is founded by settlers out of ${nameOf(parent)}"
      )
    }
  }

  /** The best unsettled site within reach of one settlement: close first, then good. */
  private fun bestSiteNear(parent: Town, civ: Civ, year: Int): Int? {
    val range = civ.culture.expansionRange
    var best = -1
    var bestScore = 0.0

    for (candidate in facts) {
      if (towns[candidate.index].founded != 0) continue
      if (candidate.habitability < params.minFoundingHabitability) continue

      val reach = distance[candidate.index][parent.facts.index]
      if (reach > range || reach <= 0.0) continue

      val score = candidate.habitability * (1.0 - reach / range) *
          (1.0 + 0.3 * roll(year.toLong(), candidate.index.toLong(), EXPAND_SALT))
      if (score > bestScore) {
        bestScore = score
        best = candidate.index
      }
    }

    return best.takeIf { it >= 0 }
  }

  private fun advanceTechnology(civ: Civ, year: Int) {
    val before = civ.technology
    // Trade raises it: a civ with many settlements advances faster than one with the same people in one
    // place, which is the cheapest way to make connectivity matter without a trade simulation.
    val connectivity = (civ.towns.count { towns[it].standing } / 8.0).coerceAtMost(1.0)
    civ.technology = (civ.technology +
        params.technologyRate * params.yearsPerTick * civ.culture.inventiveness *
        (0.6 + 0.4 * connectivity)).coerceAtMost(1.0)

    // Log the crossings of each quarter, so the chronicle records an era rather than a slope.
    val step = 0.25
    if (Math.floor(civ.technology / step) > Math.floor(before / step)) {
      log(
        year, EventKind.TECHNOLOGY, listOf(Actor(ActorType.CIV, civ.index)), null, emptyList(),
        "the ${civName(civ)} master ${technologyLabel(civ.technology)}"
      )
    }
  }

  private fun technologyLabel(level: Double): String = when {
    level < 0.3 -> "iron and the ard plough"
    level < 0.55 -> "the heavy plough and the water mill"
    level < 0.8 -> "stone vaulting and the blast bloomery"
    else -> "the printing press and the deep shaft"
  }

  // --- Diplomacy and war -----------------------------------------------------------------------------

  /**
   * Hostility accrues between civs that are in contact, faster when they compete for the same ground.
   *
   * Proximity and *cultural similarity* both drive it, which is the counter-intuitive part and the right
   * one: two farming peoples on the same river want the same fields, while a farming people and a herding
   * one occupy different ground and mostly trade. Wars between neighbours who want the same thing is what
   * produces a border that moves, rather than a random scatter of grievances.
   */
  private fun updateRelations(year: Int) {
    for (a in civs.indices) {
      if (!civs[a].exists) continue
      for (b in a + 1 until civs.size) {
        if (!civs[b].exists) continue

        val gap = frontierDistance(civs[a], civs[b])
        if (gap > params.contactRange) continue

        val proximity = 1.0 - (gap / params.contactRange)
        val competition = if (civs[a].cultureIndex == civs[b].cultureIndex) 1.3 else 0.7
        val appetite = (civs[a].culture.bellicosity + civs[b].culture.bellicosity) * 0.5

        val gain = params.hostilityRate * proximity * competition * appetite *
            (0.7 + 0.6 * roll(year.toLong(), a.toLong(), b.toLong(), HOSTILITY_SALT))

        hostility[a][b] += gain
        hostility[b][a] = hostility[a][b]
      }
    }
  }

  /** Metres between the two nearest standing settlements of two civs, or infinity if either has none. */
  private fun frontierDistance(a: Civ, b: Civ): Double {
    var best = Double.MAX_VALUE
    for (i in a.towns) {
      if (!towns[i].standing) continue
      for (j in b.towns) {
        if (!towns[j].standing) continue
        best = min(best, distance[i][j])
      }
    }
    return best
  }

  private fun resolveWars(year: Int) {
    for (a in civs.indices) {
      for (b in a + 1 until civs.size) {
        if (hostility[a][b] < params.warThreshold) continue
        if (!civs[a].exists || !civs[b].exists) continue
        if (wars.any { it.involves(a, b) }) continue

        declareWar(a, b, year)
      }
    }

    val ended = ArrayList<War>()
    for (war in wars) {
      if (!civs[war.a].exists || !civs[war.b].exists) {
        ended.add(war)
        continue
      }

      fightBattle(war, year)

      val length = params.minWarYears +
          (roll(war.startedYear.toLong(), war.a.toLong(), war.b.toLong(), WAR_LENGTH_SALT) *
              (params.maxWarYears - params.minWarYears)).toInt()
      if (war.decided || year - war.startedYear >= length) {
        makePeace(war, year)
        ended.add(war)
      }
    }
    wars.removeAll(ended)
  }

  private fun declareWar(a: Int, b: Int, year: Int) {
    val cause = log(
      year, EventKind.WAR_DECLARED,
      listOf(Actor(ActorType.CIV, a), Actor(ActorType.CIV, b)), null, emptyList(),
      "the ${civName(civs[a])} go to war with the ${civName(civs[b])}"
    )

    // The grudge points at the declaration, not at a number. An NPC can cite an event; it cannot cite a
    // hostility score - which is the difference between "they burned Ashford in 412" and "we dislike them".
    civs[a].grudges.add(b to cause)
    civs[b].grudges.add(a to cause)

    hostility[a][b] = WAR_HOSTILITY_RESET
    hostility[b][a] = WAR_HOSTILITY_RESET
    wars.add(War(a, b, year, cause))
  }

  /**
   * One engagement per tick per war, at the frontier: the two nearest settlements of the two sides.
   *
   * The battle happens between them rather than at one of them, and that midpoint becomes a battlefield
   * site - which is why a world's battlefields end up strung along the borders that were contested rather
   * than scattered evenly, without anything having to model a border.
   */
  private fun fightBattle(war: War, year: Int) {
    var attacker = -1
    var defender = -1
    var best = Double.MAX_VALUE

    for (i in civs[war.a].towns) {
      if (!towns[i].standing) continue
      for (j in civs[war.b].towns) {
        if (!towns[j].standing) continue
        if (distance[i][j] < best) {
          best = distance[i][j]
          attacker = i
          defender = j
        }
      }
    }
    if (attacker < 0 || defender < 0) {
      war.decided = true
      return
    }

    val strengthA = strengthOf(civs[war.a])
    val strengthB = strengthOf(civs[war.b])
    val aWins = roll(year.toLong(), war.a.toLong(), war.b.toLong(), BATTLE_SALT) <
        strengthA / (strengthA + strengthB)

    val winner = if (aWins) war.a else war.b
    val loser = if (aWins) war.b else war.a
    val target = if (aWins) defender else attacker
    val midpoint = towns[attacker].facts.position.lerp(towns[defender].facts.position, 0.5)

    val casualties = ((strengthA + strengthB) * BATTLE_CASUALTY_SHARE).toInt().coerceAtLeast(20)
    val battle = log(
      year, EventKind.BATTLE,
      listOf(Actor(ActorType.CIV, winner), Actor(ActorType.CIV, loser)),
      midpoint, listOf(war.cause),
      "the ${civName(civs[winner])} defeat the ${civName(civs[loser])} near ${nameOf(towns[target])}, " +
          "$casualties dead"
    )

    val siteIndex = addSite(
      SiteKind.BATTLEFIELD, midpoint, year, target, winner,
      radius = BATTLEFIELD_RADIUS, artifact = -1, figure = -1
    )
    towns[target].sites.add(siteIndex)

    // Losing a battle costs the loser's frontier settlement people whether or not it is then taken.
    towns[target].population = max(10.0, towns[target].population * (1.0 - BATTLE_POPULATION_LOSS))

    killGeneralsOf(loser, year, battle, midpoint)
    besiege(war, winner, loser, target, year, battle)
  }

  private fun strengthOf(civ: Civ): Double {
    val subjects = civ.towns.filter { towns[it].standing }.sumOf { towns[it].population }
    return max(1.0, subjects * civ.culture.militaryBias * (0.6 + 0.4 * civ.technology))
  }

  /**
   * A siege: the settlement is sacked, taken, or razed.
   *
   * Razing is gated on having been sacked before, which is what makes ruins *cluster* on contested
   * frontiers rather than appear at random. A place that has burned twice is a place worth abandoning.
   */
  private fun besiege(war: War, winner: Int, loser: Int, target: Int, year: Int, cause: Int) {
    val town = towns[target]
    val fortified = town.wallYear != 0
    val outcome = roll(year.toLong(), target.toLong(), SIEGE_SALT)

    // Walls are the whole point of walls: they turn most sieges into a siege that failed.
    if (fortified && outcome < WALL_HOLDS_CHANCE) {
      log(
        year, EventKind.SIEGE, listOf(Actor(ActorType.SETTLEMENT, target), Actor(ActorType.CIV, winner)),
        town.facts.position, listOf(cause),
        "the walls of ${nameOf(town)} hold against the ${civName(civs[winner])}"
      )
      return
    }

    town.sacked++
    val sack = log(
      year, EventKind.SETTLEMENT_SACKED,
      listOf(Actor(ActorType.SETTLEMENT, target), Actor(ActorType.CIV, winner), Actor(ActorType.CIV, loser)),
      town.facts.position, listOf(cause),
      "${nameOf(town)} is sacked by the ${civName(civs[winner])}"
    )
    town.population = max(8.0, town.population * (1.0 - SACK_POPULATION_LOSS))
    town.wealth *= 0.6
    // The hinterland is wrecked along with the town, and it does not all come back. This is what makes a
    // twice-sacked place permanently smaller than its site could support, rather than one that regrows to the
    // same size and leaves the sacking with no lasting consequence.
    town.decline *= SACK_DECLINE

    // A small settlement can be gone after one sack; a city takes more. Gated on the tier rather than on the
    // count alone, because the count alone made ruins vanishingly rare - three in a world of two hundred and
    // ninety-two - and a world with no ruins in it has no visible history.
    val fragility = if (town.facts.tier.ordinal >= SettlementTier.VILLAGE.ordinal) 2.0 else 1.0
    val raze = roll(year.toLong(), target.toLong(), RAZE_SALT) <
        RAZE_CHANCE * town.sacked * fragility
    if (raze) {
      abandon(town, year, EventKind.SETTLEMENT_RAZED, winner, "razed by the ${civName(civs[winner])}")
      war.decided = true
      return
    }

    // Conquest rather than a second sack: ownership changes and the place takes the victor's name, with
    // the old one kept because that is what the conquered go on calling it.
    if (roll(year.toLong(), target.toLong(), CONQUEST_SALT) < CONQUEST_CHANCE) {
      civs[loser].towns.remove(target)
      civs[winner].towns.add(target)
      town.owner = winner
      town.oldNameSeed = town.nameSeed
      town.nameSeed = Names.seedOf(worldSeed, PLACE_NAME_SALT, target.toLong(), year.toLong())

      log(
        year, EventKind.CONQUEST,
        listOf(Actor(ActorType.SETTLEMENT, target), Actor(ActorType.CIV, winner)),
        town.facts.position, listOf(sack),
        "${nameOf(town)} passes to the ${civName(civs[winner])}, who rename it from " +
            Names.place(town.oldNameSeed, civs[loser].cultureIndex)
      )
      war.decided = true
    }
  }

  private fun makePeace(war: War, year: Int) {
    log(
      year, EventKind.PEACE, listOf(Actor(ActorType.CIV, war.a), Actor(ActorType.CIV, war.b)),
      null, listOf(war.cause),
      "the ${civName(civs[war.a])} and the ${civName(civs[war.b])} come to terms"
    )
  }

  // --- Disasters -------------------------------------------------------------------------------------

  /**
   * Plague, famine, flood and eruption, each gated on something about the place.
   *
   * The gating is what makes these history rather than noise. A plague needs people close together, so it
   * hits cities; a famine needs poor soil and a bad year, so it hits the marginal ground a civ expanded
   * onto under pressure; a flood needs a floodplain, which is the same ground the settlement was placed on
   * *because* it was fertile. The last one is the tension worth having in a world - the good land is the
   * dangerous land.
   */
  private fun resolveDisasters(year: Int) {
    // One climate roll for the whole world per tick, so a famine year is a famine year everywhere - which
    // is what makes it a historical event rather than a per-settlement coin flip.
    val harvest = roll(year.toLong(), HARVEST_SALT)

    for (town in towns) {
      if (!town.standing) continue
      val index = town.facts.index.toLong()

      val plagueRisk = (town.population / 9_000.0).coerceAtMost(0.55) * params.yearsPerTick / 5.0
      if (roll(year.toLong(), index, PLAGUE_SALT) < plagueRisk * PLAGUE_RATE) {
        val dead = (town.population * (0.22 + 0.2 * roll(year.toLong(), index, PLAGUE_TOLL_SALT))).toInt()
        town.population -= dead
        town.population = max(6.0, town.population)
        log(
          year, EventKind.PLAGUE, listOf(Actor(ActorType.SETTLEMENT, town.facts.index)),
          town.facts.position, emptyList(),
          "plague takes $dead in ${nameOf(town)}"
        )
      }

      if (harvest < FAMINE_YEAR_CHANCE) {
        val vulnerability = 1.0 - town.facts.fertility
        if (roll(year.toLong(), index, FAMINE_SALT) < vulnerability * FAMINE_RATE) {
          val dead = (town.population * (0.08 + 0.12 * vulnerability)).toInt()
          town.population = max(6.0, town.population - dead)
          log(
            year, EventKind.FAMINE, listOf(Actor(ActorType.SETTLEMENT, town.facts.index)),
            town.facts.position, emptyList(),
            "the harvest fails at ${nameOf(town)}; $dead starve"
          )
        }
      }

      if (roll(year.toLong(), index, FLOOD_SALT) < town.facts.floodRisk * FLOOD_RATE) {
        val dead = (town.population * 0.05).toInt() + 2
        town.population = max(6.0, town.population - dead)
        town.wealth *= 0.85
        log(
          year, EventKind.FLOOD, listOf(Actor(ActorType.SETTLEMENT, town.facts.index)),
          town.facts.position, emptyList(),
          "the river takes the lower town at ${nameOf(town)}"
        )
      }

      if (roll(year.toLong(), index, ERUPTION_SALT) < town.facts.volcanism * ERUPTION_RATE) {
        abandon(town, year, EventKind.ERUPTION, null, "buried in ash")
      }
    }
  }

  private fun abandon(town: Town, year: Int, cause: EventKind, byCiv: Int?, how: String) {
    if (!town.standing) return

    town.abandoned = year
    town.ruinCause = cause
    val owner = town.owner
    if (owner >= 0) civs[owner].towns.remove(town.facts.index)
    town.owner = -1

    val actors = ArrayList<Actor>()
    actors.add(Actor(ActorType.SETTLEMENT, town.facts.index))
    byCiv?.let { actors.add(Actor(ActorType.CIV, it)) }

    log(
      year, cause, actors, town.facts.position, emptyList(),
      "${nameOf(town)} is $how and stands empty"
    )

    // The ruin field is bigger than the town was, because a burned town spreads: the earthworks, the field
    // walls and the middens outlast the buildings. Capped, because the materialiser finds a point marker by
    // expanding a chunk's bounds by a fixed margin, and a ruin reaching past that margin would simply stop
    // at a straight line - see ChunkMaterializer.MARKER_MARGIN.
    val ruin = addSite(
      SiteKind.RUIN, town.facts.position, year, town.facts.index,
      civ = town.foundingCiv,
      radius = min(town.facts.tier.footprintRadius * RUIN_SPREAD, MAX_RUIN_RADIUS),
      artifact = -1, figure = -1
    )
    town.sites.add(ruin)
    town.population = 0.0
  }

  // --- Figures and artifacts -------------------------------------------------------------------------

  private fun updateFigures(year: Int) {
    for (civ in civs) {
      if (!civ.exists) continue

      val chance = params.yearsPerTick.toDouble() / params.yearsPerFigure
      if (roll(year.toLong(), civ.index.toLong(), FIGURE_SALT) >= chance) continue

      val home = civ.towns.filter { towns[it].standing }
        .maxByOrNull { towns[it].population } ?: continue
      val role = roleFor(civ, year)
      val person = Person(
        index = people.size,
        nameSeed = Names.seedOf(worldSeed, FIGURE_NAME_SALT, people.size.toLong()),
        role = role,
        civ = civ.index,
        home = home,
        birth = year
      )
      people.add(person)

      log(
        year, EventKind.FIGURE_ROSE,
        listOf(Actor(ActorType.FIGURE, person.index), Actor(ActorType.CIV, civ.index)),
        towns[home].facts.position, emptyList(),
        "${personName(person)} rises as ${article(role)} of the ${civName(civ)} at ${nameOf(towns[home])}"
      )
    }

    for (person in people) {
      if (person.death != 0) continue
      if (year - person.birth < params.figureLifespan) continue
      person.death = year
      buryFigure(person, year, EventKind.FIGURE_DIED, "dies of age")
    }
  }

  /** Roles are drawn against culture: a herding people produces generals, a mining people smiths. */
  private fun roleFor(civ: Civ, year: Int): FigureRole {
    val culture = civ.culture
    val weights = doubleArrayOf(
      1.0,                            // RULER
      culture.militaryBias,           // GENERAL
      culture.clergyBias,             // PROPHET
      culture.craftBias,              // SMITH
      culture.harbour + 0.3,          // EXPLORER
      culture.inventiveness           // SCHOLAR
    )
    var pick = roll(year.toLong(), civ.index.toLong(), ROLE_SALT) * weights.sum()
    for (i in weights.indices) {
      pick -= weights[i]
      if (pick <= 0.0) return FigureRole.entries[i]
    }
    return FigureRole.RULER
  }

  /** A general of the losing side may fall in the battle rather than in bed. */
  private fun killGeneralsOf(civ: Int, year: Int, cause: Int, where: Vec2d) {
    for (person in people) {
      if (person.death != 0 || person.civ != civ) continue
      if (person.role != FigureRole.GENERAL && person.role != FigureRole.RULER) continue
      if (roll(year.toLong(), person.index.toLong(), SLAIN_SALT) >= SLAIN_CHANCE) continue

      person.death = year
      person.slainAt = where
      buryFigure(person, year, EventKind.FIGURE_SLAIN, "falls in battle", cause)
    }
  }

  /**
   * A dead figure gets a tomb if they were notable enough, and their artifact goes in with them.
   *
   * This is the join that makes an artifact findable. The provenance chain ends at a site, the site is a
   * marker in the world, and the marker is what a chunk materialises - so "the sword was lost with him at
   * Ashford" is a place a player can dig.
   */
  private fun buryFigure(person: Person, year: Int, kind: EventKind, how: String, cause: Int = -1) {
    val civ = civs[person.civ]
    val died = log(
      year, kind, listOf(Actor(ActorType.FIGURE, person.index), Actor(ActorType.CIV, person.civ)),
      person.slainAt ?: towns[person.home].facts.position,
      if (cause >= 0) listOf(cause) else emptyList(),
      "${personName(person)} $how"
    )

    val entombed = person.role == FigureRole.RULER || person.role == FigureRole.PROPHET ||
        relics.any { it.holder == person.index }
    if (!entombed) return

    val relic = relics.firstOrNull { it.holder == person.index }
    val site = addSite(
      SiteKind.TOMB,
      // Beside the home settlement rather than in it: a barrow is outside the walls, and putting it under
      // the market square would have the town materialise a tomb through its own buildings.
      offset(towns[person.home].facts.position, person.index.toLong(), TOMB_OFFSET),
      year, person.home, person.civ,
      radius = TOMB_RADIUS, artifact = relic?.index ?: -1, figure = person.index
    )
    person.resting = site
    towns[person.home].sites.add(site)

    if (relic != null) {
      relic.holder = -1
      relic.resting = site
      relic.provenance.add(
        log(
          year, EventKind.ARTIFACT_ENTOMBED,
          listOf(Actor(ActorType.ARTIFACT, relic.index), Actor(ActorType.FIGURE, person.index),
            Actor(ActorType.SITE, site)),
          sites[site].position, listOf(died),
          "${relicName(relic)} is laid in the tomb of ${personName(person)}"
        )
      )
    }
  }

  private fun updateArtifacts(year: Int) {
    // Smiths forge; rulers commission. Either way it takes a figure, which is what ties an artifact to a
    // person and a place instead of leaving it a loot table entry.
    for (person in people) {
      if (person.death != 0) continue
      if (person.role != FigureRole.SMITH && person.role != FigureRole.RULER) continue
      if (relics.any { it.holder == person.index }) continue
      if (roll(year.toLong(), person.index.toLong(), FORGE_SALT) >= FORGE_CHANCE) continue

      val civ = civs[person.civ]
      val kind = ArtifactKind.entries[
        (roll(year.toLong(), person.index.toLong(), ARTIFACT_KIND_SALT) * ArtifactKind.entries.size)
          .toInt().coerceAtMost(ArtifactKind.entries.size - 1)
      ]
      val relic = Relic(
        index = relics.size,
        nameSeed = Names.seedOf(worldSeed, ARTIFACT_NAME_SALT, relics.size.toLong()),
        kind = kind,
        forgedYear = year,
        forgedBy = person.index,
        material = materialFor(kind, towns[person.home], civ),
        forgedAtNameSeed = towns[person.home].nameSeed,
        holder = person.index
      )
      relics.add(relic)

      relic.provenance.add(
        log(
          year, EventKind.ARTIFACT_FORGED,
          listOf(Actor(ActorType.ARTIFACT, relic.index), Actor(ActorType.FIGURE, person.index)),
          towns[person.home].facts.position, emptyList(),
          "${personName(person)} makes ${relicName(relic)} of ${relic.material} at ${nameOf(towns[person.home])}"
        )
      )
    }

    // An artifact whose holder is dead and which was not entombed is loose in the world, and gets picked
    // up by someone else - or lost in the ruin where it was last seen.
    for (relic in relics) {
      if (relic.resting >= 0 || relic.holder < 0) continue
      val holder = people[relic.holder]
      if (holder.death == 0) continue

      val heir = people.firstOrNull { it.death == 0 && it.civ == holder.civ }
      if (heir != null && roll(year.toLong(), relic.index.toLong(), INHERIT_SALT) < INHERIT_CHANCE) {
        relic.holder = heir.index
        relic.provenance.add(
          log(
            year, EventKind.ARTIFACT_TAKEN,
            listOf(Actor(ActorType.ARTIFACT, relic.index), Actor(ActorType.FIGURE, heir.index)),
            towns[heir.home].facts.position, emptyList(),
            "${relicName(relic)} passes to ${personName(heir)}"
          )
        )
      } else {
        // Lost, and lost *somewhere*: a ruin if the holder's home is one, otherwise a battlefield of their
        // civ, otherwise their home. A quest hook needs a location or it is only a rumour.
        val where = lostPlaceFor(holder)
        relic.resting = where
        relic.holder = -1
        relic.provenance.add(
          log(
            year, EventKind.ARTIFACT_LOST,
            listOf(Actor(ActorType.ARTIFACT, relic.index), Actor(ActorType.SITE, where)),
            sites[where].position, emptyList(),
            "${relicName(relic)} is lost at ${siteName(sites[where])}"
          )
        )
      }
    }
  }

  private fun lostPlaceFor(holder: Person): Int {
    towns[holder.home].sites.firstOrNull { sites[it].kind == SiteKind.RUIN }?.let { return it }
    sites.firstOrNull { it.kind == SiteKind.BATTLEFIELD && it.civ == holder.civ }?.let { return it.index }

    // Nothing suitable exists yet, so the loss makes its own site: a barrow beside the holder's home.
    return addSite(
      SiteKind.TOMB, offset(towns[holder.home].facts.position, holder.index.toLong(), TOMB_OFFSET),
      holder.death, holder.home, holder.civ, TOMB_RADIUS, artifact = -1, figure = holder.index
    )
  }

  private fun materialFor(kind: ArtifactKind, town: Town, civ: Civ): String = when {
    kind == ArtifactKind.TOME -> "vellum"
    kind == ArtifactKind.BANNER -> "dyed wool"
    town.facts.resourceValue > 0.6 && civ.technology > 0.5 -> "steel and silver"
    civ.technology > 0.35 -> "wrought iron"
    else -> "bronze"
  }

  // --- Monuments and walls ---------------------------------------------------------------------------

  private fun raiseMonuments(year: Int) {
    for (civ in civs) {
      if (!civ.exists || civ.technology < MONUMENT_TECHNOLOGY) continue
      val capital = civ.towns.filter { towns[it].standing }.maxByOrNull { towns[it].population } ?: continue
      val town = towns[capital]
      if (town.population < MONUMENT_POPULATION) continue
      if (town.sites.any { sites[it].kind == SiteKind.MONUMENT }) continue
      if (roll(year.toLong(), civ.index.toLong(), MONUMENT_SALT) >= MONUMENT_CHANCE) continue

      val site = addSite(
        SiteKind.MONUMENT, offset(town.facts.position, capital.toLong(), MONUMENT_OFFSET),
        year, capital, civ.index, MONUMENT_RADIUS, artifact = -1, figure = -1
      )
      town.sites.add(site)

      log(
        year, EventKind.MONUMENT_BUILT,
        listOf(Actor(ActorType.SITE, site), Actor(ActorType.CIV, civ.index)),
        sites[site].position, emptyList(),
        "the ${civName(civ)} raise ${siteName(sites[site])} at ${nameOf(town)}"
      )
    }
  }

  // --- The four built sites --------------------------------------------------------------------------

  /**
   * Mines, monasteries, forts and lighthouses: the things a civilisation puts up on purpose.
   *
   * One pass in [raiseMonuments]' shape - gated, once in a while, per civ, with an event logged - because that
   * shape is what makes the lore free rather than a second system. A site founded here gets a name from
   * [siteName] (and [Names.site]'s `else` branch handles a new form with no edit to `Names`), can hold a relic
   * whose provenance chain already ends at a *site*, and is mined by `chronicle -Pquests` for unresolved
   * threads without a line of new code.
   *
   * **Where** each may go was decided by the terrain, in [SpecialSiteCandidates]. What this adds is the half
   * that needs a thousand years: whether the civ has the technology, whether it has a reason, and whether it
   * has already built one recently. That division is the architecture document's "history does not place
   * settlements" rule applied one level down.
   */
  private fun buildSites(year: Int) {
    for (civ in civs) {
      if (!civ.exists) continue

      buildSite(
        year, civ, SiteKind.MINE, candidates.mines, params.mineTechnology, MINE_SALT,
        EventKind.MINE_OPENED, MINE_RADIUS
      ) { candidate ->
        // A mine belongs to whichever of this civ's standing towns is near enough to work it.
        nearestStandingTown(civ, candidate.position, params.mineRange) != null
      }

      buildSite(
        year, civ, SiteKind.MONASTERY, candidates.monasteries, params.monasteryTechnology, MONASTERY_SALT,
        EventKind.MONASTERY_FOUNDED, MONASTERY_RADIUS
      ) { candidate ->
        // Remoteness is the site's defining property and was already enforced against *every* settlement when
        // the candidate was found. What is left is reach: a civ does not found a house it cannot walk to.
        nearestStandingTown(civ, candidate.position, params.fortRange) != null
      }

      buildSite(
        year, civ, SiteKind.FORT, candidates.forts, params.fortTechnology, FORT_SALT,
        EventKind.FORT_BUILT, FORT_RADIUS
      ) { candidate ->
        // A fort needs a *reason*, and the reason is somebody on the other side of the hill. `frontierDistance`
        // already computes how close two civs' territories come, and was written for the hostility model.
        val neighbour = civs.any { other ->
          other.exists && other.index != civ.index &&
              frontierDistance(civ, other) < params.contactRange
        }
        neighbour && nearestStandingTown(civ, candidate.position, params.fortRange) != null
      }

      buildSite(
        year, civ, SiteKind.LIGHTHOUSE, candidates.lighthouses, params.lighthouseTechnology, LIGHTHOUSE_SALT,
        EventKind.LIGHTHOUSE_LIT, LIGHTHOUSE_RADIUS
      ) { candidate ->
        // `SiteFacts.coastal` was computed and never read by anything until now, so this reader can only add.
        // The port pays for the light, so there has to be one: a coastal town of this civ within reach.
        val port = nearestStandingTown(civ, candidate.position, params.fortRange)
        port != null && towns[port].facts.coastal
      }
    }
  }

  /**
   * One gated, once-in-a-while founding of a built site.
   *
   * Shared by all four kinds because the differences between them are entirely in [reason] and in which
   * candidate list they draw from - the gating, the interval, the roll, the event and the provenance are the
   * same shape for a mine and for a lighthouse, and writing that shape four times is four places for it to
   * drift.
   *
   * Candidates are tried best-first and the first that passes [reason] and is not already taken wins, so the
   * ranking [SpecialSiteCandidates] computed is what decides *which* of them gets built.
   */
  private inline fun buildSite(
    year: Int,
    civ: Civ,
    kind: SiteKind,
    from: List<SiteCandidate>,
    technology: Double,
    salt: Long,
    event: EventKind,
    radius: Double,
    reason: (SiteCandidate) -> Boolean
  ) {
    if (from.isEmpty()) return
    if (civ.technology < technology) return

    // Spread over the history rather than all in the decade the technology threshold was crossed - the same
    // argument `foundingChance` makes about settlements.
    //
    // Nullable rather than an `Int.MIN_VALUE` sentinel, which is how this first went wrong and produced **zero
    // built sites of any kind on every world** while every candidate list was full and technology reached 0.88.
    // `year - Int.MIN_VALUE` overflows to a large negative, so `< builtSiteInterval` was true on the first tick
    // and every tick after it: the gate meant to space foundings out rejected all of them. A sentinel that has
    // to survive arithmetic is a sentinel in the wrong place.
    val last = civ.lastBuilt[kind]
    if (last != null && year - last < params.builtSiteInterval) return
    if (roll(year.toLong(), civ.index.toLong(), salt) >= params.builtSiteChance) return

    for (candidate in from) {
      // One site per place, whoever builds it. Two civs putting a fort on the same saddle is a war, not two
      // forts, and the map would show one on top of the other.
      if (sites.any { it.kind == kind && it.position.distanceTo(candidate.position) < params.siteSeparation }) {
        continue
      }
      if (!reason(candidate)) continue

      val host = nearestStandingTown(civ, candidate.position, params.fortRange) ?: continue
      val site = addSite(
        kind, candidate.position, year, host, civ.index, radius, artifact = -1, figure = -1,
        resource = candidate.detail
      )
      towns[host].sites.add(site)

      log(
        year, event,
        listOf(Actor(ActorType.SITE, site), Actor(ActorType.CIV, civ.index)),
        candidate.position, emptyList(),
        "the ${civName(civ)} ${verbFor(kind)} ${siteName(sites[site])} near ${nameOf(towns[host])}"
      )

      civ.lastBuilt[kind] = year
      return
    }
  }

  /** The civ's nearest standing town to a point, within [within] metres, or null. */
  private fun nearestStandingTown(civ: Civ, at: Vec2d, within: Double): Int? = civ.towns
    .filter { towns[it].standing }
    .minByOrNull { towns[it].facts.position.distanceTo(at) }
    ?.takeIf { towns[it].facts.position.distanceTo(at) <= within }

  private fun verbFor(kind: SiteKind): String = when (kind) {
    SiteKind.MINE -> "open"
    SiteKind.MONASTERY -> "found"
    SiteKind.FORT -> "raise"
    SiteKind.LIGHTHOUSE -> "light"
    // The residue kinds are never built by this pass; the branch exists so adding a kind is a compile error.
    SiteKind.RUIN, SiteKind.BATTLEFIELD, SiteKind.TOMB, SiteKind.MONUMENT -> "make"
  }

  /**
   * A settlement walls itself once it has been attacked and can afford to.
   *
   * Both conditions matter, and the *order* of them is why walls are worth simulating rather than
   * assigning: a town walls the extent it had when it was threatened, and then keeps growing. Later growth
   * spills outside the circuit, which is exactly what real cities did and is visible from a long way off.
   */
  private fun buildWalls(year: Int) {
    for (town in towns) {
      if (!town.standing || town.wallYear != 0) continue
      if (town.sacked == 0) continue
      if (town.population < params.wallPopulation) continue

      town.wallYear = year
      town.wallPopulation = town.population.toInt()
      log(
        year, EventKind.SETTLEMENT_WALLED, listOf(Actor(ActorType.SETTLEMENT, town.facts.index)),
        town.facts.position, emptyList(),
        "${nameOf(town)} builds its walls after ${town.sacked} sacking(s)"
      )
    }
  }

  private fun retireCivs(year: Int) {
    for (civ in civs) {
      if (!civ.exists) continue
      if (civ.towns.any { towns[it].standing }) continue

      civ.ended = year
      log(
        year, EventKind.CIV_FELL, listOf(Actor(ActorType.CIV, civ.index)), null, emptyList(),
        "the ${civName(civ)} are no more"
      )
    }
  }

  // --- Assembling the chronicle ----------------------------------------------------------------------

  private fun assemble(): Chronicle {
    val kept = prune()

    return Chronicle(
      startYear = startYear,
      presentYear = presentYear,
      events = kept,
      civs = civs.map { civ ->
        CivRecord(
          index = civ.index, cultureIndex = civ.cultureIndex, nameSeed = civ.nameSeed,
          foundedYear = civ.founded, endedYear = civ.ended, capital = civ.capital,
          technology = civ.technology, peakPopulation = civ.peak,
          settlements = civ.towns.sorted(), grudges = civ.grudges.toList()
        )
      },
      figures = people.map {
        FigureRecord(
          index = it.index, nameSeed = it.nameSeed, role = it.role, civ = it.civ,
          homeSettlement = it.home, birthYear = it.birth, deathYear = it.death,
          restingSite = it.resting
        )
      },
      artifacts = relics.map {
        ArtifactRecord(
          index = it.index, nameSeed = it.nameSeed, kind = it.kind, forgedYear = it.forgedYear,
          forgedBy = it.forgedBy, material = it.material, forgedAtNameSeed = it.forgedAtNameSeed,
          restingSite = it.resting, provenance = it.provenance.toList()
        )
      },
      sites = sites.map {
        SiteRecord(
          index = it.index, kind = it.kind, position = it.position, year = it.year,
          settlement = it.settlement, civ = it.civ, radius = it.radius,
          decay = decayOf(it), nameSeed = it.nameSeed, artifact = it.artifact, figure = it.figure,
          resource = it.resource
        )
      },
      settlements = towns.map { town ->
        SettlementRecord(
          index = town.facts.index, foundedYear = town.founded, abandonedYear = town.abandoned,
          population = town.population.toInt(), wealth = town.wealth.coerceIn(0.0, 1.0),
          ownerCiv = town.owner, foundingCiv = town.foundingCiv, timesSacked = town.sacked,
          wallYear = town.wallYear, wallPopulation = town.wallPopulation,
          nameSeed = town.nameSeed, oldNameSeed = town.oldNameSeed, ruinCause = town.ruinCause,
          sites = town.sites.toList()
        )
      },
      prunedEvents = prunedEvents
    )
  }

  /**
   * Importance pruning: keep everything notable, sample the rest, and never break a causal chain.
   *
   * The last clause is the one that takes care. Dropping an event that a surviving event cites as a cause
   * leaves a dangling id, and a provenance chain with a hole in it is worse than a shorter chain - so
   * anything referenced by a keeper is kept regardless of its own importance, and so is every event on an
   * artifact's chain.
   */
  private fun prune(): List<HistoryEvent> {
    val keep = LinkedHashSet<Int>()

    for (event in events) {
      val notable = event.importance >= params.importanceFloor
      val sampled = event.id % params.minorEventSampling == 0
      if (notable || sampled) keep.add(event.id)
    }
    for (relic in relics) keep.addAll(relic.provenance)

    // Transitive closure over causes, so nothing kept cites something dropped.
    var frontier = keep.toList()
    while (frontier.isNotEmpty()) {
      val next = ArrayList<Int>()
      for (id in frontier) {
        for (cause in events[id].causes) {
          if (keep.add(cause)) next.add(cause)
        }
      }
      frontier = next
    }

    prunedEvents = events.size - keep.size
    return events.filter { it.id in keep }
  }

  /** How gone a site is by the present day. A century-old ruin is rubble; a millennium-old one is a mound. */
  private fun decayOf(site: Site): Double {
    val age = (presentYear - site.year).coerceAtLeast(0)
    return (age / DECAY_YEARS).coerceIn(0.0, 1.0)
  }

  // --- Small helpers ---------------------------------------------------------------------------------

  private fun log(
    year: Int,
    kind: EventKind,
    actors: List<Actor>,
    where: Vec2d?,
    causes: List<Int>,
    detail: String
  ): Int {
    val id = nextEventId++
    events.add(
      HistoryEvent(
        id = id, year = year, kind = kind, actors = actors, where = where, causes = causes,
        importance = kind.baseImportance, detail = detail
      )
    )
    return id
  }

  private fun addSite(
    kind: SiteKind,
    position: Vec2d,
    year: Int,
    settlement: Int,
    civ: Int,
    radius: Double,
    artifact: Int,
    figure: Int,
    resource: Int = -1
  ): Int {
    val index = sites.size
    sites.add(
      Site(
        index = index, kind = kind, position = position, year = year, settlement = settlement,
        civ = civ, radius = radius,
        nameSeed = Names.seedOf(worldSeed, SITE_NAME_SALT, index.toLong()),
        artifact = artifact, figure = figure, resource = resource
      )
    )
    return index
  }

  /**
   * A point [metres] from [from], in a direction fixed by [key].
   *
   * Deterministic and not a random walk: two runs must put the same barrow in the same field, and a tomb
   * whose position depended on how many tombs had been placed already would move whenever anything
   * upstream changed.
   */
  private fun offset(from: Vec2d, key: Long, metres: Double): Vec2d {
    val angle = roll(key, OFFSET_SALT) * 2.0 * Math.PI
    return Vec2d(from.x + Math.cos(angle) * metres, from.y + Math.sin(angle) * metres)
  }

  private fun roll(vararg key: Long): Double = GenRng.hashUnit(streamBase, *key)

  private fun nameOf(town: Town) = Names.place(town.nameSeed, town.facts.cultureIndex)

  private fun civName(civ: Civ) = Names.civ(civ.nameSeed, civ.cultureIndex)

  private fun personName(person: Person) =
    Names.person(person.nameSeed, civs[person.civ].cultureIndex, person.role)

  private fun relicName(relic: Relic) = Names.artifact(
    relic.nameSeed, civs[people[relic.forgedBy].civ].cultureIndex, relic.kind, relic.forgedAtNameSeed
  )

  private fun siteName(site: Site): String {
    val culture = if (site.civ >= 0) civs[site.civ].cultureIndex else 0
    val of = if (site.settlement >= 0) nameOf(towns[site.settlement]) else "the wilds"
    return Names.site(
      site.nameSeed, culture, of,
      when (site.kind) {
        SiteKind.RUIN -> "ruin"
        SiteKind.BATTLEFIELD -> "field"
        SiteKind.TOMB -> "barrow"
        SiteKind.MONUMENT -> "monument"
        // `Names.site` ends in `else -> "the $form of $of"`, so a new form needs no edit to `Names` at all -
        // which is the mechanism that makes the lore for these four free.
        SiteKind.MINE -> "mine"
        SiteKind.MONASTERY -> "abbey"
        SiteKind.FORT -> "fort"
        SiteKind.LIGHTHOUSE -> "light"
      }
    )
  }

  private fun article(role: FigureRole) = when (role) {
    FigureRole.RULER -> "a ruler"
    FigureRole.GENERAL -> "a general"
    FigureRole.PROPHET -> "a prophet"
    FigureRole.SMITH -> "a smith"
    FigureRole.EXPLORER -> "an explorer"
    FigureRole.SCHOLAR -> "a scholar"
  }

  // --- Mutable simulation state ---------------------------------------------------------------------

  private class Town(val facts: SiteFacts) {
    var founded = 0
    var abandoned = 0
    var population = 0.0
    var wealth = 0.0
    var owner = -1
    var foundingCiv = -1
    var sacked = 0

    /** Multiplier on carrying capacity, reduced by each sacking. See [besiege]. */
    var decline = 1.0
    var wallYear = 0
    var wallPopulation = 0
    var nameSeed = 0L
    var oldNameSeed = 0L
    var ruinCause: EventKind? = null
    val sites = ArrayList<Int>()

    val standing get() = founded != 0 && abandoned == 0
  }

  private class Civ(val index: Int, val cultureIndex: Int, val capital: Int) {
    var nameSeed = 0L
    var founded = 0
    var ended = 0
    var technology = 0.0
    var peak = 0
    val towns = ArrayList<Int>()
    val grudges = ArrayList<Pair<Int, Int>>()

    /** Year this civ last built each kind of site, so they arrive across a history instead of all at once. */
    val lastBuilt = HashMap<SiteKind, Int>()

    val exists get() = ended == 0
    val culture: Culture get() = Culture.byIndex(cultureIndex)
  }

  private class Person(
    val index: Int,
    val nameSeed: Long,
    val role: FigureRole,
    val civ: Int,
    val home: Int,
    val birth: Int
  ) {
    var death = 0
    var resting = -1
    var slainAt: Vec2d? = null
  }

  private class Relic(
    val index: Int,
    val nameSeed: Long,
    val kind: ArtifactKind,
    val forgedYear: Int,
    val forgedBy: Int,
    val material: String,
    val forgedAtNameSeed: Long,
    var holder: Int
  ) {
    var resting = -1
    val provenance = ArrayList<Int>()
  }

  private class Site(
    val index: Int,
    val kind: SiteKind,
    val position: Vec2d,
    val year: Int,
    val settlement: Int,
    val civ: Int,
    val radius: Double,
    val nameSeed: Long,
    val artifact: Int,
    val figure: Int,
    /** [net.bestia.worldgen.resource.ResourceType] ordinal for a mine, -1 for everything else. */
    val resource: Int = -1
  )

  private class War(val a: Int, val b: Int, val startedYear: Int, val cause: Int) {
    /** Set when a razing or a conquest settles the matter, which ends the war early. */
    var decided = false

    fun involves(x: Int, y: Int) = (a == x && b == y) || (a == y && b == x)
  }

  private companion object {

    /** Share of its eventual capacity a settlement starts at when founded. A founding party, not a seed. */
    const val INITIAL_OCCUPANCY = 0.20

    const val WAR_HOSTILITY_RESET = 0.25
    const val BATTLE_CASUALTY_SHARE = 0.03
    const val BATTLE_POPULATION_LOSS = 0.06
    const val SACK_POPULATION_LOSS = 0.35

    /** Chance a walled settlement's circuit holds against a siege. Walls are meant to work. */
    const val WALL_HOLDS_CHANCE = 0.62

    const val RAZE_CHANCE = 0.12

    /** What one sacking permanently costs a settlement's carrying capacity. */
    const val SACK_DECLINE = 0.82
    const val CONQUEST_CHANCE = 0.30
    const val SLAIN_CHANCE = 0.22

    /**
     * Plague chance per tick at the population where the risk factor saturates.
     *
     * Tuned against the chronicle rather than against a feeling, which is what the `chronicle` tool is for: at
     * 0.14 a city of five thousand caught plague eight times in a thousand years and never grew enough to
     * found anything, so the whole world stayed one city. A major epidemic every two to three centuries is
     * about the historical rate and leaves room for a town to recover between them.
     */
    const val PLAGUE_RATE = 0.045
    const val PLAGUE_TOLL_SALT = 0x91L
    const val FAMINE_YEAR_CHANCE = 0.12
    const val FAMINE_RATE = 0.22
    const val FLOOD_RATE = 0.07

    /**
     * Eruption chance per tick, scaled by how volcanic the ground is.
     *
     * Tiny, and it has to be: this is the one disaster that destroys a settlement outright, so the rate
     * multiplied by two hundred ticks is roughly the share of arc-side settlements that end as ruins. At 0.004
     * that was eighty percent of a volcanic site's odds over a millennium, and the chronicle came back with
     * thirty-five towns buried in ash against two razed by war - a world whose ruins were geology rather than
     * history, which is precisely backwards for a stage whose point is history.
     */
    const val ERUPTION_RATE = 0.0004

    const val FORGE_CHANCE = 0.30
    const val INHERIT_CHANCE = 0.55

    const val MONUMENT_TECHNOLOGY = 0.35
    const val MONUMENT_POPULATION = 2_500.0
    const val MONUMENT_CHANCE = 0.06
    const val MONUMENT_RADIUS = 26.0

    // All four well under ChunkMaterializer.MARKER_MARGIN (320 m), which is what
    // `checkStructuralMarkersFitTheQueryMargin` enforces - a site wider than the chunk query margin is simply
    // absent from every chunk further away than it and materialises with a dead straight edge down one side.
    const val MINE_RADIUS = 34.0
    const val MONASTERY_RADIUS = 40.0
    const val FORT_RADIUS = 46.0
    const val LIGHTHOUSE_RADIUS = 14.0
    const val MONUMENT_OFFSET = 140.0

    const val TOMB_RADIUS = 11.0
    const val TOMB_OFFSET = 320.0
    const val BATTLEFIELD_RADIUS = 180.0

    /** A ruin field spreads beyond the town that made it: earthworks and field walls outlast buildings. */
    const val RUIN_SPREAD = 1.25

    /**
     * Largest radius a ruin field may have, in metres.
     *
     * Must stay below `ChunkMaterializer.MARKER_MARGIN`. Not read from it, because `history` is a sibling of
     * `voxel` and siblings do not call into each other - so this is a duplicated number with a tripwire
     * (`Invariants.checkStructuralMarkersFitTheQueryMargin`) rather than a shared constant.
     */
    const val MAX_RUIN_RADIUS = 260.0

    /** Years over which a site decays from fresh to earthworks. */
    const val DECAY_YEARS = 900.0

    // Salts. Distinct constants rather than ad-hoc numbers so that two decisions can never share a stream
    // by accident - which would correlate them in a way nobody would ever look for.
    const val CIV_NAME_SALT = 0x01L
    const val PLACE_NAME_SALT = 0x02L
    const val FIGURE_NAME_SALT = 0x03L
    const val ARTIFACT_NAME_SALT = 0x04L
    const val SITE_NAME_SALT = 0x05L

    const val EXPAND_SALT = 0x11L
    const val HOSTILITY_SALT = 0x12L
    const val WAR_LENGTH_SALT = 0x13L
    const val BATTLE_SALT = 0x14L
    const val SIEGE_SALT = 0x15L
    const val RAZE_SALT = 0x16L
    const val CONQUEST_SALT = 0x17L
    const val HARVEST_SALT = 0x18L
    const val PLAGUE_SALT = 0x19L
    const val FAMINE_SALT = 0x1AL
    const val FLOOD_SALT = 0x1BL
    const val ERUPTION_SALT = 0x1CL
    const val FIGURE_SALT = 0x1DL
    const val ROLE_SALT = 0x1EL
    const val SLAIN_SALT = 0x1FL
    const val FORGE_SALT = 0x20L
    const val ARTIFACT_KIND_SALT = 0x21L
    const val INHERIT_SALT = 0x22L
    const val MONUMENT_SALT = 0x23L
    const val OFFSET_SALT = 0x24L

    // The four built sites. 0x25 onward was free; each kind needs its own so a civ's roll for a mine in one
    // year is independent of its roll for a fort in the same year.
    const val MINE_SALT = 0x25L
    const val MONASTERY_SALT = 0x26L
    const val FORT_SALT = 0x27L
    const val LIGHTHOUSE_SALT = 0x28L
  }
}
