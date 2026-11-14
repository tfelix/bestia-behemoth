package net.bestia.worldgen.history

import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.ArtifactKind
import net.bestia.worldgen.core.ArtifactRecord
import net.bestia.worldgen.core.BeastKind
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.CivOrigin
import net.bestia.worldgen.core.CivRecord
import net.bestia.worldgen.core.CultFate
import net.bestia.worldgen.core.CultKind
import net.bestia.worldgen.core.CultRecord
import net.bestia.worldgen.core.DeityRecord
import net.bestia.worldgen.core.Domain
import net.bestia.worldgen.core.DynastyRecord
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.Faction
import net.bestia.worldgen.core.FigureRecord
import net.bestia.worldgen.core.FigureRole
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.HistoryEvent
import net.bestia.worldgen.core.LairKind
import net.bestia.worldgen.core.MonsterFate
import net.bestia.worldgen.core.MonsterOrigin
import net.bestia.worldgen.core.MonsterRecord
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.RelationKind
import net.bestia.worldgen.core.RelationRecord
import net.bestia.worldgen.core.SettlementRecord
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.SiteRecord
import net.bestia.worldgen.core.ThreadRecord
import net.bestia.worldgen.core.WorldBoss
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
  val resourceValue: Double,

  /**
   * 0 to 1: how much mana this place is exposed to. A **proximity maximum**, not a point sample.
   *
   * The raw field is rank-normalised over land by `ManaStage`, so 0.8 means "the top fifth of the world's mana
   * is within reach of this town". `HistoryStage.manaField` is where the reach is defined and where the choice
   * against a point sample is argued - and where the measurement that refuted the *first* argument for it is
   * recorded.
   */
  val mana: Double = 0.0
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

  // --- Families -------------------------------------------------------------------------------------

  /** Chance a new figure is the child of an earlier one of their people. */
  val childChance: Double = 0.35,

  /** Years between a parent's rise and their child's at the least, and how long a dead parent still counts. */
  val generationYears: Int = 25,

  /** Chance a dead ruler leaves an heir; otherwise the house ends in a succession crisis. */
  val heirChance: Double = 0.75,

  /** Chance a succession crisis sends the old house into exile, where it founds a people. */
  val crisisExileChance: Double = 0.25,

  /** Chance a succession crisis splits the civ instead, when it is large enough to split. */
  val crisisSplitChance: Double = 0.25,

  /** Chance per tick that two peoples at peace with living, unmarried rulers wed them to each other. */
  val marriageChance: Double = 0.005,

  /** Chance that the two generals of a battle become sworn enemies. */
  val feudChance: Double = 0.3,

  /** Chance that kin of a figure slain in battle swear vengeance. */
  val vengeanceChance: Double = 0.5,

  /** Chance per siege that a living general of the besieged people betrays the town to the enemy. */
  val betrayalChance: Double = 0.06,

  // --- Gods and cults -------------------------------------------------------------------------------

  /** Gods a world of the reference size starts with, before a coin flip adds one. */
  val deitiesAtReference: Double = 4.0,

  /** More gods per doubling of the world's settlement sites. */
  val deitiesPerDoubling: Double = 1.5,
  val maxDeities: Int = 12,

  /** Chance per tick that a civ's first eligible prophet founds a cult. */
  val cultChance: Double = 0.03,

  /** Chance per tick that a cult wins over another town. */
  val cultSpreadChance: Double = 0.15,

  /** Metres from a town that keeps a cult to one it can win over. */
  val cultSpreadRange: Double = 40_000.0,

  /** Chance per tick that the people holding most of a cult's towns turns on it. */
  val cultPersecutionChance: Double = 0.02,

  /** Of persecutions: the share that wipes the cult out, and the share that makes it rise as a realm. */
  val cultWipeShare: Double = 0.4,
  val cultRevoltShare: Double = 0.3,

  /** Chance per tick, on a world with the Orders, that a cult joins the Order it leans toward. */
  val cultAbsorbChance: Double = 0.01,

  // --- World bosses ---------------------------------------------------------------------------------

  /** World bosses a reference world wakes, before a coin flip adds one, and more per doubling of its sites. */
  val monstersAtReference: Double = 2.0,
  val monstersPerDoubling: Double = 1.0,
  val maxMonsters: Int = 5,

  /** Years between which a world boss wakes. */
  val monsterFrom: Int = 150,
  val monsterTo: Int = 850,

  /** Years a world boss rages before its story ends. */
  val monsterReignMin: Int = 15,
  val monsterReignMax: Int = 60,

  /** Chance per tick that a raging beast falls on the nearest town within [ravageRange] metres. */
  val ravageChance: Double = 0.4,
  val ravageRange: Double = 40_000.0,
  /** Share of its people a town loses to a raging beast. */
  val ravageLoss: Double = 0.25,

  /** Dead on one battlefield for its dead to rise as a world boss. */
  val risenDeadFallen: Int = 500,

  /** Relative weights of how a world boss's story ends. */
  val slainWeight: Double = 0.4,
  val sealedWeight: Double = 0.25,
  val withdrawnWeight: Double = 0.25,
  val vanishedWeight: Double = 0.1,

  /** Metres of water a sea-going world boss's lair needs over it. */
  val oceanLairDepth: Double = 30.0,

  /** A pair of facing towns is on the front when it is at most this many times as far apart as the closest pair. */
  val frontierSpread: Double = 1.5,

  /** Metres within which a battle adds to an existing battlefield rather than making a new one. */
  val battleMergeRadius: Double = 1_500.0,

  /** Metres a battle may be fought to either side of the line between the two towns. */
  val battleAside: Double = 1_500.0,

  /** Chance a battle returns to an old field on the way between the two towns instead of opening a new one. */
  val oldGroundChance: Double = 0.5,

  // --- New peoples ----------------------------------------------------------------------------------
  //
  // Every path below is optional: a world may see any of them, several times, or none. The chances are per
  // world of the reference size and grow with the number of settlement sites.

  /** Settlement sites on a genesis-size world (128 km). A world with twice as many sees new peoples twice as often. */
  val referenceSites: Double = 28.0,

  /** Expected arrivals over the sea on a reference world, spread over the years between the two bounds. */
  val arrivalChance: Double = 0.35,
  val arrivalFrom: Int = 150,
  val arrivalTo: Int = 750,

  /** Expected migrations overland on a reference world, spread over the years between the two bounds. */
  val migrationChance: Double = 0.3,
  val migrationFrom: Int = 100,
  val migrationTo: Int = 700,

  /** Metres a newcomer keeps from every standing town. Nobody lands next to a city and founds a people. */
  val newcomerClearance: Double = 15_000.0,

  /** Standing towns a civ needs before any of them can break away. */
  val secessionMinTowns: Int = 5,

  /** Chance per tick that a civ's most aggrieved town rises, at full grievance. */
  val revoltRate: Double = 0.02,

  /** Chance that a revolt wins; a lost one costs the town people and changes nothing else. */
  val revoltSuccess: Double = 0.6,

  /** Hostility a broken-away people starts with toward the one it left. Near [warThreshold] on purpose. */
  val secessionHostility: Double = 0.8,

  /** Chance that survivors of a razed, buried or forsaken town found a people of their own. */
  val refugeeChance: Double = 0.35,

  /** Population a lost town must have had for enough survivors to found anything. */
  val refugeeMinPopulation: Double = 300.0,

  /** Metres survivors travel at most to find empty land. */
  val refugeeRange: Double = 90_000.0,

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

  /**
   * Metres from a sacked town within which its people could have run to a cave with the treasury.
   *
   * Shorter than [mineRange], and for a reason worth keeping: a mine is opened over years by people who went
   * looking, and a hoard is hidden in an afternoon by people who are being chased. A day's flight, not a
   * survey.
   */
  val hoardRange: Double = 25_000.0,

  /** Chance that a sacking is the one where somebody hides the treasury and does not come back for it. */
  val hoardChance: Double = 0.30,

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

  // --- The mana -------------------------------------------------------------------------------------
  //
  // Every threshold here is stated in units of [SiteFacts.mana], which `ManaStage` rank-normalises over land -
  // so each one is readable as a percentile of the world's own mana rather than as a magic number, and none of
  // them is comparable across worlds. `BiomeStage.rankConfidence` makes the same trade for the same reason.
  //
  // The *rates* live in the simulation's companion beside `PLAGUE_RATE` and `ERUPTION_RATE`, because a rate is
  // a statement about how often history happens and a threshold is a statement about where.

  /**
   * Metres within which a mana province counts as reaching a settlement's fields.
   *
   * Raw, not detail-scaled: a province is 8-13 km across at every world size, so this is a real distance. Nine
   * kilometres puts a town on the edge of a neighbouring province at risk and a town two provinces away in the
   * clear, which is the structure that makes the blight a property of *where* a town is.
   */
  val blightRange: Double = 9_000.0,

  /**
   * Mana exposure at which a settlement's fields begin to fail.
   *
   * The top quarter or so of the world's mana. Deliberately **below** [woundMana] and below the corruption
   * threshold `CorruptionStage` solves for (which lands near the 0.90 percentile by construction): the towns
   * with a story worth telling are the ones on the *fringe* of a blighted province, not the ones inside it -
   * nobody was ever living inside one.
   */
  val blightMana: Double = 0.72,

  /**
   * How many blights a settlement survives before its people give up and walk away.
   *
   * A count rather than a chance, so that being forsaken is the *end* of a documented decline rather than a
   * die roll: the chronicle has three or four `BLIGHT_SPREAD` lines and then a `SETTLEMENT_FORSAKEN`, which is
   * a story. A per-tick abandonment chance would empty towns with nothing leading up to it.
   */
  val blightsBeforeForsaking: Int = 4,

  /** Technology a civ needs before it knows how to ward its fields at all. */
  val wardTechnology: Double = 0.40,

  /**
   * Mana a place needs before it can be a wound.
   *
   * The top tenth of the world's land. Note what this does *not* do: it does not guarantee a wound exists,
   * because the peak may be at sea, under a lake or inside a town's clearance - see `SpecialSites.wounds`. A
   * world with no wound is a legitimate answer and `Invariants` reports the count rather than requiring one.
   */
  val woundMana: Double = 0.90,

  /** Metres a wound keeps from every settlement site. See `SpecialSites.wounds` for both reasons. */
  val woundClearance: Double = 8_000.0,

  /** Metres between two wounds. A province apart, because closer than that they are one place. */
  val woundSeparation: Double = 45_000.0,

  /** Most wounds one world may have. The star broke up on the way down, or it did not. */
  val maxWounds: Int = 3,

  /**
   * Metres a seer will travel towards a wound.
   *
   * Generous - much further than a mine's or a fort's reach - because this is one person choosing to go rather
   * than a civilisation deciding to work somewhere, and the whole point of the event is that they went a long
   * way and did not come back.
   */
  val seerRange: Double = 70_000.0,

  /** Years between notable figures in a civ, roughly. */
  val yearsPerFigure: Int = 35,

  val figureLifespan: Int = 62,

  /**
   * Importance below which an event is only sampled rather than kept.
   *
   * The log has to be prunable or it is unbounded, and what is actually below the floor today is
   * [EventKind.FIGURE_DIED], [EventKind.TECHNOLOGY] and [EventKind.FLOOD] - a few hundred lines of texture
   * across a thousand years. Everything at or above this is kept forever; below it, one in
   * [minorEventSampling] survives, so the texture is still there and the volume is not.
   *
   * This used to cite a per-settlement growth event as the thing being pruned. `EventKind.SETTLEMENT_GREW`
   * existed for that and **was never logged by anything**, so the sentence described a hypothetical; it was
   * deleted along with `FIGURE_BORN`, which was dead the same way. Worth recording that the tempting fix -
   * actually logging growth - was declined: it would insert tens of thousands of events and shift every event
   * id in every world, and event ids are what a `RUIN` marker's station channel and every `causes` list point
   * at.
   */
  val importanceFloor: Int = 40,

  val minorEventSampling: Int = 24,

  // --- The Orders -------------------------------------------------------------------------------------

  /**
   * How much of this world's history each Order shaped. See [OrderInfluence].
   *
   * [OrderInfluence.NONE] by default, which means the Orders are absent from the chronicle entirely - the
   * first incarnation's answer, and the one that leaves this stage's output identical to what it produced
   * before the Orders existed.
   */
  val orderInfluence: OrderInfluence = OrderInfluence.NONE,

  /**
   * Share of civilisations that ever swear to an Order at all, 0 to 1.
   *
   * The main restraint on the whole subsystem, and it is a *share* rather than a chance per tick so that the
   * answer is decided once per civilisation and does not drift with how long the civ happens to survive. At
   * 0.6 two civs in five are unaligned for their whole history, which is what keeps the Orders a thread
   * running through a world's past rather than the subject of it.
   *
   * Ignored entirely when [orderInfluence] is absent.
   */
  val orderSwornShare: Double = 0.6,

  /**
   * Chance per tick that a civilisation with the means and the reason takes an oath.
   *
   * A chance rather than a certainty, on `foundingChance`'s argument: oaths that all land in the decade the
   * gate opened would date every sworn people in the world to the same year and leave the chronicle with
   * nothing to say about the rest of its span.
   */
  val orderSwearChance: Double = 0.09,

  /**
   * Chance per tick that a sworn civilisation whose experience contradicts its Order abandons it.
   *
   * Small, because a schism should be the exception a chronicle remembers rather than a periodic event. It is
   * gated on contradiction as well as on this roll, so a people whose convictions match what happened to them
   * never rolls at all.
   */
  val schismChance: Double = 0.02,

  /** Chance per tick that a sworn civilisation performs its signature working somewhere it matters. */
  val riteChance: Double = 0.10,

  /** Technology a civilisation needs before it can raise a shrine. Lower than a mine; it is a stone circle. */
  val shrineTechnology: Double = 0.20,

  /** Metres a shrine keeps from every settlement. Far enough to be a journey, near enough to be tended. */
  val shrineClearance: Double = 4_000.0,

  /** Metres from one of its own settlements within which a civ could have raised and kept a shrine. */
  val shrineRange: Double = 40_000.0,

  /**
   * Mana at which ground is a frontier worth warding, for an Eternity shrine.
   *
   * Below [blightMana] on purpose: a ward stone goes up on the *approach* to blighted ground, at the edge of
   * where the failing harvests start, not inside a province nobody was ever living in.
   */
  val shrineFrontierMana: Double = 0.55
) : Params {

  init {
    require(years >= 0) { "years must not be negative, was $years" }
    // A zero step is an infinite loop rather than a wrong world, which is the one failure mode here that
    // does not announce itself.
    require(yearsPerTick >= 1) { "yearsPerTick must be at least 1, was $yearsPerTick" }
    require(growthRate >= 0.0) { "growthRate must not be negative, was $growthRate" }
    require(technologyRate >= 0.0) { "technologyRate must not be negative, was $technologyRate" }
    require(minFoundingHabitability in 0.0..1.0) {
      "minFoundingHabitability must be in [0,1], was $minFoundingHabitability"
    }
    require(expansionPressure > 0.0) { "expansionPressure must be positive, was $expansionPressure" }
    require(foundingChance in 0.0..1.0) { "foundingChance must be in [0,1], was $foundingChance" }
    require(foundingsPerTick >= 0) { "foundingsPerTick must not be negative, was $foundingsPerTick" }
    require(hostilityRate >= 0.0) { "hostilityRate must not be negative, was $hostilityRate" }
    require(warThreshold > 0.0) { "warThreshold must be positive, was $warThreshold" }
    require(contactRange >= 0.0) { "contactRange must not be negative, was $contactRange" }
    require(minWarYears in 0..maxWarYears) {
      "minWarYears $minWarYears must be in [0, maxWarYears $maxWarYears]"
    }
    require(wallPopulation >= 0) { "wallPopulation must not be negative, was $wallPopulation" }
    require(childChance in 0.0..1.0) { "childChance must be in [0,1], was $childChance" }
    require(generationYears >= 1) { "generationYears must be at least 1, was $generationYears" }
    require(heirChance in 0.0..1.0) { "heirChance must be in [0,1], was $heirChance" }
    require(crisisExileChance >= 0.0 && crisisSplitChance >= 0.0 && crisisExileChance + crisisSplitChance <= 1.0) {
      "crisisExileChance $crisisExileChance and crisisSplitChance $crisisSplitChance must be non-negative " +
          "and sum to at most 1"
    }
    require(marriageChance in 0.0..1.0) { "marriageChance must be in [0,1], was $marriageChance" }
    require(feudChance in 0.0..1.0) { "feudChance must be in [0,1], was $feudChance" }
    require(vengeanceChance in 0.0..1.0) { "vengeanceChance must be in [0,1], was $vengeanceChance" }
    require(betrayalChance in 0.0..1.0) { "betrayalChance must be in [0,1], was $betrayalChance" }
    require(deitiesAtReference >= 0.0) { "deitiesAtReference must not be negative, was $deitiesAtReference" }
    require(deitiesPerDoubling >= 0.0) { "deitiesPerDoubling must not be negative, was $deitiesPerDoubling" }
    require(maxDeities >= 1) { "maxDeities must be at least 1, was $maxDeities" }
    require(cultChance in 0.0..1.0) { "cultChance must be in [0,1], was $cultChance" }
    require(cultSpreadChance in 0.0..1.0) { "cultSpreadChance must be in [0,1], was $cultSpreadChance" }
    require(cultSpreadRange >= 0.0) { "cultSpreadRange must not be negative, was $cultSpreadRange" }
    require(cultPersecutionChance in 0.0..1.0) { "cultPersecutionChance must be in [0,1], was $cultPersecutionChance" }
    require(cultWipeShare >= 0.0 && cultRevoltShare >= 0.0 && cultWipeShare + cultRevoltShare <= 1.0) {
      "cultWipeShare $cultWipeShare and cultRevoltShare $cultRevoltShare must be non-negative and sum to at most 1"
    }
    require(cultAbsorbChance in 0.0..1.0) { "cultAbsorbChance must be in [0,1], was $cultAbsorbChance" }
    require(monstersAtReference >= 0.0 && monstersPerDoubling >= 0.0 && maxMonsters >= 0) {
      "monster counts must not be negative: $monstersAtReference, $monstersPerDoubling, $maxMonsters"
    }
    require(monsterFrom in 0..monsterTo) { "monsterFrom $monsterFrom must be in [0, monsterTo $monsterTo]" }
    require(monsterReignMin in 0..monsterReignMax) {
      "monsterReignMin $monsterReignMin must be in [0, monsterReignMax $monsterReignMax]"
    }
    require(ravageChance in 0.0..1.0 && ravageLoss in 0.0..1.0 && ravageRange >= 0.0) {
      "ravage chance and loss must be in [0,1] and range non-negative: $ravageChance, $ravageLoss, $ravageRange"
    }
    require(risenDeadFallen >= 0) { "risenDeadFallen must not be negative, was $risenDeadFallen" }
    require(listOf(slainWeight, sealedWeight, withdrawnWeight, vanishedWeight).all { it >= 0.0 }) {
      "fate weights must not be negative"
    }
    require(oceanLairDepth >= 0.0) { "oceanLairDepth must not be negative, was $oceanLairDepth" }
    require(frontierSpread >= 1.0) { "frontierSpread must be at least 1, was $frontierSpread" }
    require(battleMergeRadius >= 0.0) { "battleMergeRadius must not be negative, was $battleMergeRadius" }
    require(battleAside >= 0.0) { "battleAside must not be negative, was $battleAside" }
    require(oldGroundChance in 0.0..1.0) { "oldGroundChance must be in [0,1], was $oldGroundChance" }
    require(referenceSites > 0.0) { "referenceSites must be positive, was $referenceSites" }
    require(arrivalChance >= 0.0) { "arrivalChance must not be negative, was $arrivalChance" }
    require(arrivalFrom in 0..arrivalTo) { "arrivalFrom $arrivalFrom must be in [0, arrivalTo $arrivalTo]" }
    require(migrationChance >= 0.0) { "migrationChance must not be negative, was $migrationChance" }
    require(migrationFrom in 0..migrationTo) {
      "migrationFrom $migrationFrom must be in [0, migrationTo $migrationTo]"
    }
    require(newcomerClearance >= 0.0) { "newcomerClearance must not be negative, was $newcomerClearance" }
    require(secessionMinTowns >= 2) { "secessionMinTowns must be at least 2, was $secessionMinTowns" }
    require(revoltRate in 0.0..1.0) { "revoltRate must be in [0,1], was $revoltRate" }
    require(revoltSuccess in 0.0..1.0) { "revoltSuccess must be in [0,1], was $revoltSuccess" }
    require(secessionHostility >= 0.0) { "secessionHostility must not be negative, was $secessionHostility" }
    require(refugeeChance in 0.0..1.0) { "refugeeChance must be in [0,1], was $refugeeChance" }
    require(refugeeMinPopulation >= 0.0) {
      "refugeeMinPopulation must not be negative, was $refugeeMinPopulation"
    }
    require(refugeeRange >= 0.0) { "refugeeRange must not be negative, was $refugeeRange" }

    require(mineRichness in 0.0..1.0) { "mineRichness must be in [0,1], was $mineRichness" }
    require(mineDepth > 0.0) { "mineDepth must be positive, was $mineDepth" }
    require(mineRange >= 0.0) { "mineRange must not be negative, was $mineRange" }
    require(monasteryClearance >= 0.0) { "monasteryClearance must not be negative, was $monasteryClearance" }
    require(fortClearance >= 0.0) { "fortClearance must not be negative, was $fortClearance" }
    require(fortRange >= fortClearance) {
      "fortRange $fortRange is inside fortClearance $fortClearance, so no fort site can satisfy both"
    }
    require(saddleSpan > 0.0) { "saddleSpan must be positive, was $saddleSpan" }
    require(saddleRelief >= 0.0) { "saddleRelief must not be negative, was $saddleRelief" }
    require(lighthouseRange >= 0.0) { "lighthouseRange must not be negative, was $lighthouseRange" }
    require(lighthouseClearance >= 0.0) { "lighthouseClearance must not be negative, was $lighthouseClearance" }
    require(siteFreeboard >= 0.0) { "siteFreeboard must not be negative, was $siteFreeboard" }
    require(lighthouseFreeboard >= 0.0) { "lighthouseFreeboard must not be negative, was $lighthouseFreeboard" }
    require(siteSeparation >= 0.0) { "siteSeparation must not be negative, was $siteSeparation" }
    require(candidateStride > 0.0) { "candidateStride must be positive, was $candidateStride" }
    require(maxCandidates >= 1) { "maxCandidates must be at least 1, was $maxCandidates" }
    require(mineTechnology in 0.0..1.0) { "mineTechnology must be in [0,1], was $mineTechnology" }
    require(monasteryTechnology in 0.0..1.0) { "monasteryTechnology must be in [0,1], was $monasteryTechnology" }
    require(fortTechnology in 0.0..1.0) { "fortTechnology must be in [0,1], was $fortTechnology" }
    require(lighthouseTechnology in 0.0..1.0) {
      "lighthouseTechnology must be in [0,1], was $lighthouseTechnology"
    }
    require(builtSiteChance in 0.0..1.0) { "builtSiteChance must be in [0,1], was $builtSiteChance" }
    require(builtSiteInterval >= 0) { "builtSiteInterval must not be negative, was $builtSiteInterval" }

    require(blightRange > 0.0) { "blightRange must be positive, was $blightRange" }
    require(blightMana in 0.0..1.0) { "blightMana must be in [0,1], was $blightMana" }
    require(blightsBeforeForsaking >= 1) {
      "blightsBeforeForsaking must be at least 1, was $blightsBeforeForsaking"
    }
    require(wardTechnology in 0.0..1.0) { "wardTechnology must be in [0,1], was $wardTechnology" }
    require(woundMana in 0.0..1.0) { "woundMana must be in [0,1], was $woundMana" }
    // Below the wound threshold, or every town at risk of blight is inside a wound's clearance and the two
    // halves of the subsystem can never both fire on one world.
    require(blightMana <= woundMana) {
      "blightMana $blightMana must not exceed woundMana $woundMana, or no blighted town is ever near a wound"
    }
    require(woundClearance >= 0.0) { "woundClearance must not be negative, was $woundClearance" }
    require(woundSeparation >= 0.0) { "woundSeparation must not be negative, was $woundSeparation" }
    require(maxWounds >= 0) { "maxWounds must not be negative, was $maxWounds" }
    require(seerRange >= 0.0) { "seerRange must not be negative, was $seerRange" }
    require(yearsPerFigure >= 1) { "yearsPerFigure must be at least 1, was $yearsPerFigure" }
    require(figureLifespan >= 1) { "figureLifespan must be at least 1, was $figureLifespan" }
    require(importanceFloor >= 0) { "importanceFloor must not be negative, was $importanceFloor" }
    // Sampling is `index % n == 0`, so zero would divide by zero and one keeps everything.
    require(minorEventSampling >= 1) { "minorEventSampling must be at least 1, was $minorEventSampling" }

    require(orderSwornShare in 0.0..1.0) { "orderSwornShare must be in [0,1], was $orderSwornShare" }
    require(orderSwearChance in 0.0..1.0) { "orderSwearChance must be in [0,1], was $orderSwearChance" }
    require(schismChance in 0.0..1.0) { "schismChance must be in [0,1], was $schismChance" }
    require(riteChance in 0.0..1.0) { "riteChance must be in [0,1], was $riteChance" }
    require(shrineTechnology in 0.0..1.0) { "shrineTechnology must be in [0,1], was $shrineTechnology" }
    require(shrineClearance >= 0.0) { "shrineClearance must not be negative, was $shrineClearance" }
    require(shrineRange >= shrineClearance) {
      "shrineRange $shrineRange is inside shrineClearance $shrineClearance, so no shrine site can satisfy both"
    }
    require(shrineFrontierMana in 0.0..1.0) {
      "shrineFrontierMana must be in [0,1], was $shrineFrontierMana"
    }
    // A ward stone marks the approach to blighted ground. Above the blight threshold it would only ever be
    // sited inside a province where no town had fields to lose, and the Eternity shrine would never be raised.
    require(shrineFrontierMana <= blightMana) {
      "shrineFrontierMana $shrineFrontierMana must not exceed blightMana $blightMana, or a ward stone can " +
          "only stand inside ground that was never farmed"
    }
  }

  /**
   * This params object with anything the file set applied. See [net.bestia.worldgen.pipeline.WorldParams.load].
   *
   * Every field, including the ones that were unreachable for as long as this class was on
   * `WorldParams.NOT_YET_LOADABLE` - which was all of them. `orderInfluence` is the reason the loader was
   * finally written: the Orders' weights are the one number in here a *server* has to set per world rather
   * than a designer setting once, and there was no path for it.
   */
  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    years = source.int("years", years),
    yearsPerTick = source.int("yearsPerTick", yearsPerTick),
    growthRate = source.double("growthRate", growthRate),
    technologyRate = source.double("technologyRate", technologyRate),
    minFoundingHabitability = source.double("minFoundingHabitability", minFoundingHabitability),
    expansionPressure = source.double("expansionPressure", expansionPressure),
    foundingChance = source.double("foundingChance", foundingChance),
    foundingsPerTick = source.int("foundingsPerTick", foundingsPerTick),
    hostilityRate = source.double("hostilityRate", hostilityRate),
    warThreshold = source.double("warThreshold", warThreshold),
    contactRange = source.double("contactRange", contactRange),
    minWarYears = source.int("minWarYears", minWarYears),
    maxWarYears = source.int("maxWarYears", maxWarYears),
    wallPopulation = source.int("wallPopulation", wallPopulation),
    childChance = source.double("childChance", childChance),
    generationYears = source.int("generationYears", generationYears),
    heirChance = source.double("heirChance", heirChance),
    crisisExileChance = source.double("crisisExileChance", crisisExileChance),
    crisisSplitChance = source.double("crisisSplitChance", crisisSplitChance),
    marriageChance = source.double("marriageChance", marriageChance),
    feudChance = source.double("feudChance", feudChance),
    vengeanceChance = source.double("vengeanceChance", vengeanceChance),
    betrayalChance = source.double("betrayalChance", betrayalChance),
    deitiesAtReference = source.double("deitiesAtReference", deitiesAtReference),
    deitiesPerDoubling = source.double("deitiesPerDoubling", deitiesPerDoubling),
    maxDeities = source.int("maxDeities", maxDeities),
    cultChance = source.double("cultChance", cultChance),
    cultSpreadChance = source.double("cultSpreadChance", cultSpreadChance),
    cultSpreadRange = source.double("cultSpreadRange", cultSpreadRange),
    cultPersecutionChance = source.double("cultPersecutionChance", cultPersecutionChance),
    cultWipeShare = source.double("cultWipeShare", cultWipeShare),
    cultRevoltShare = source.double("cultRevoltShare", cultRevoltShare),
    cultAbsorbChance = source.double("cultAbsorbChance", cultAbsorbChance),
    monstersAtReference = source.double("monstersAtReference", monstersAtReference),
    monstersPerDoubling = source.double("monstersPerDoubling", monstersPerDoubling),
    maxMonsters = source.int("maxMonsters", maxMonsters),
    monsterFrom = source.int("monsterFrom", monsterFrom),
    monsterTo = source.int("monsterTo", monsterTo),
    monsterReignMin = source.int("monsterReignMin", monsterReignMin),
    monsterReignMax = source.int("monsterReignMax", monsterReignMax),
    ravageChance = source.double("ravageChance", ravageChance),
    ravageRange = source.double("ravageRange", ravageRange),
    ravageLoss = source.double("ravageLoss", ravageLoss),
    risenDeadFallen = source.int("risenDeadFallen", risenDeadFallen),
    slainWeight = source.double("slainWeight", slainWeight),
    sealedWeight = source.double("sealedWeight", sealedWeight),
    withdrawnWeight = source.double("withdrawnWeight", withdrawnWeight),
    vanishedWeight = source.double("vanishedWeight", vanishedWeight),
    oceanLairDepth = source.double("oceanLairDepth", oceanLairDepth),
    frontierSpread = source.double("frontierSpread", frontierSpread),
    battleMergeRadius = source.double("battleMergeRadius", battleMergeRadius),
    battleAside = source.double("battleAside", battleAside),
    oldGroundChance = source.double("oldGroundChance", oldGroundChance),
    referenceSites = source.double("referenceSites", referenceSites),
    arrivalChance = source.double("arrivalChance", arrivalChance),
    arrivalFrom = source.int("arrivalFrom", arrivalFrom),
    arrivalTo = source.int("arrivalTo", arrivalTo),
    migrationChance = source.double("migrationChance", migrationChance),
    migrationFrom = source.int("migrationFrom", migrationFrom),
    migrationTo = source.int("migrationTo", migrationTo),
    newcomerClearance = source.double("newcomerClearance", newcomerClearance),
    secessionMinTowns = source.int("secessionMinTowns", secessionMinTowns),
    revoltRate = source.double("revoltRate", revoltRate),
    revoltSuccess = source.double("revoltSuccess", revoltSuccess),
    secessionHostility = source.double("secessionHostility", secessionHostility),
    refugeeChance = source.double("refugeeChance", refugeeChance),
    refugeeMinPopulation = source.double("refugeeMinPopulation", refugeeMinPopulation),
    refugeeRange = source.double("refugeeRange", refugeeRange),
    mineRichness = source.double("mineRichness", mineRichness),
    mineDepth = source.double("mineDepth", mineDepth),
    mineRange = source.double("mineRange", mineRange),
    hoardRange = source.double("hoardRange", hoardRange),
    hoardChance = source.double("hoardChance", hoardChance),
    monasteryClearance = source.double("monasteryClearance", monasteryClearance),
    fortClearance = source.double("fortClearance", fortClearance),
    fortRange = source.double("fortRange", fortRange),
    saddleSpan = source.double("saddleSpan", saddleSpan),
    saddleRelief = source.double("saddleRelief", saddleRelief),
    lighthouseRange = source.double("lighthouseRange", lighthouseRange),
    lighthouseClearance = source.double("lighthouseClearance", lighthouseClearance),
    siteFreeboard = source.double("siteFreeboard", siteFreeboard),
    lighthouseFreeboard = source.double("lighthouseFreeboard", lighthouseFreeboard),
    siteSeparation = source.double("siteSeparation", siteSeparation),
    candidateStride = source.double("candidateStride", candidateStride),
    maxCandidates = source.int("maxCandidates", maxCandidates),
    mineTechnology = source.double("mineTechnology", mineTechnology),
    monasteryTechnology = source.double("monasteryTechnology", monasteryTechnology),
    fortTechnology = source.double("fortTechnology", fortTechnology),
    lighthouseTechnology = source.double("lighthouseTechnology", lighthouseTechnology),
    builtSiteChance = source.double("builtSiteChance", builtSiteChance),
    builtSiteInterval = source.int("builtSiteInterval", builtSiteInterval),
    blightRange = source.double("blightRange", blightRange),
    blightMana = source.double("blightMana", blightMana),
    blightsBeforeForsaking = source.int("blightsBeforeForsaking", blightsBeforeForsaking),
    wardTechnology = source.double("wardTechnology", wardTechnology),
    woundMana = source.double("woundMana", woundMana),
    woundClearance = source.double("woundClearance", woundClearance),
    woundSeparation = source.double("woundSeparation", woundSeparation),
    maxWounds = source.int("maxWounds", maxWounds),
    seerRange = source.double("seerRange", seerRange),
    yearsPerFigure = source.int("yearsPerFigure", yearsPerFigure),
    figureLifespan = source.int("figureLifespan", figureLifespan),
    importanceFloor = source.int("importanceFloor", importanceFloor),
    minorEventSampling = source.int("minorEventSampling", minorEventSampling),
    orderInfluence = orderInfluence.overriddenBy(source.scope("orderInfluence")),
    orderSwornShare = source.double("orderSwornShare", orderSwornShare),
    orderSwearChance = source.double("orderSwearChance", orderSwearChance),
    schismChance = source.double("schismChance", schismChance),
    riteChance = source.double("riteChance", riteChance),
    shrineTechnology = source.double("shrineTechnology", shrineTechnology),
    shrineClearance = source.double("shrineClearance", shrineClearance),
    shrineRange = source.double("shrineRange", shrineRange),
    shrineFrontierMana = source.double("shrineFrontierMana", shrineFrontierMana)
  )

  override fun digest() = ParamsDigest()
    .put("years", years)
    .put("yearsPerTick", yearsPerTick)
    .put("growthRate", growthRate)
    .put("technologyRate", technologyRate)
    .put("minFoundingHabitability", minFoundingHabitability)
    .put("expansionPressure", expansionPressure)
    .put("foundingChance", foundingChance)
    .put("foundingsPerTick", foundingsPerTick)
    .put("hostilityRate", hostilityRate)
    .put("warThreshold", warThreshold)
    .put("contactRange", contactRange)
    .put("minWarYears", minWarYears)
    .put("maxWarYears", maxWarYears)
    .put("wallPopulation", wallPopulation)
    .put("childChance", childChance)
    .put("generationYears", generationYears)
    .put("heirChance", heirChance)
    .put("crisisExileChance", crisisExileChance)
    .put("crisisSplitChance", crisisSplitChance)
    .put("marriageChance", marriageChance)
    .put("feudChance", feudChance)
    .put("vengeanceChance", vengeanceChance)
    .put("betrayalChance", betrayalChance)
    .put("deitiesAtReference", deitiesAtReference)
    .put("deitiesPerDoubling", deitiesPerDoubling)
    .put("maxDeities", maxDeities)
    .put("cultChance", cultChance)
    .put("cultSpreadChance", cultSpreadChance)
    .put("cultSpreadRange", cultSpreadRange)
    .put("cultPersecutionChance", cultPersecutionChance)
    .put("cultWipeShare", cultWipeShare)
    .put("cultRevoltShare", cultRevoltShare)
    .put("cultAbsorbChance", cultAbsorbChance)
    .put("monstersAtReference", monstersAtReference)
    .put("monstersPerDoubling", monstersPerDoubling)
    .put("maxMonsters", maxMonsters)
    .put("monsterFrom", monsterFrom)
    .put("monsterTo", monsterTo)
    .put("monsterReignMin", monsterReignMin)
    .put("monsterReignMax", monsterReignMax)
    .put("ravageChance", ravageChance)
    .put("ravageRange", ravageRange)
    .put("ravageLoss", ravageLoss)
    .put("risenDeadFallen", risenDeadFallen)
    .put("slainWeight", slainWeight)
    .put("sealedWeight", sealedWeight)
    .put("withdrawnWeight", withdrawnWeight)
    .put("vanishedWeight", vanishedWeight)
    .put("oceanLairDepth", oceanLairDepth)
    .put("frontierSpread", frontierSpread)
    .put("battleMergeRadius", battleMergeRadius)
    .put("battleAside", battleAside)
    .put("oldGroundChance", oldGroundChance)
    .put("referenceSites", referenceSites)
    .put("arrivalChance", arrivalChance)
    .put("arrivalFrom", arrivalFrom)
    .put("arrivalTo", arrivalTo)
    .put("migrationChance", migrationChance)
    .put("migrationFrom", migrationFrom)
    .put("migrationTo", migrationTo)
    .put("newcomerClearance", newcomerClearance)
    .put("secessionMinTowns", secessionMinTowns)
    .put("revoltRate", revoltRate)
    .put("revoltSuccess", revoltSuccess)
    .put("secessionHostility", secessionHostility)
    .put("refugeeChance", refugeeChance)
    .put("refugeeMinPopulation", refugeeMinPopulation)
    .put("refugeeRange", refugeeRange)
    .put("mineRichness", mineRichness)
    .put("mineDepth", mineDepth)
    .put("mineRange", mineRange)
    .put("hoardRange", hoardRange)
    .put("hoardChance", hoardChance)
    .put("monasteryClearance", monasteryClearance)
    .put("fortClearance", fortClearance)
    .put("fortRange", fortRange)
    .put("saddleSpan", saddleSpan)
    .put("saddleRelief", saddleRelief)
    .put("lighthouseRange", lighthouseRange)
    .put("lighthouseClearance", lighthouseClearance)
    .put("siteFreeboard", siteFreeboard)
    .put("lighthouseFreeboard", lighthouseFreeboard)
    .put("siteSeparation", siteSeparation)
    .put("candidateStride", candidateStride)
    .put("maxCandidates", maxCandidates)
    .put("mineTechnology", mineTechnology)
    .put("monasteryTechnology", monasteryTechnology)
    .put("fortTechnology", fortTechnology)
    .put("lighthouseTechnology", lighthouseTechnology)
    .put("builtSiteChance", builtSiteChance)
    .put("builtSiteInterval", builtSiteInterval)
    .put("blightRange", blightRange)
    .put("blightMana", blightMana)
    .put("blightsBeforeForsaking", blightsBeforeForsaking)
    .put("wardTechnology", wardTechnology)
    .put("woundMana", woundMana)
    .put("woundClearance", woundClearance)
    .put("woundSeparation", woundSeparation)
    .put("maxWounds", maxWounds)
    .put("seerRange", seerRange)
    .put("yearsPerFigure", yearsPerFigure)
    .put("figureLifespan", figureLifespan)
    .put("importanceFloor", importanceFloor)
    .put("minorEventSampling", minorEventSampling)
    .nested("orderInfluence", orderInfluence.digest().value)
    .put("orderSwornShare", orderSwornShare)
    .put("orderSwearChance", orderSwearChance)
    .put("schismChance", schismChance)
    .put("riteChance", riteChance)
    .put("shrineTechnology", shrineTechnology)
    .put("shrineClearance", shrineClearance)
    .put("shrineRange", shrineRange)
    .put("shrineFrontierMana", shrineFrontierMana)
}

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
/**
 * One volcano, as history sees it.
 *
 * A vent rather than a settlement, and that is the whole restructuring: an eruption is a thing that happens to a
 * **mountain**, and which towns it buries is a consequence. Before this, each town rolled "am I buried this tick"
 * against its own distance to a fault - so two neighbouring towns on one arc were buried in different centuries
 * by different eruptions, and no event in the chronicle ever said a mountain erupted at all.
 *
 * [index] is dense from zero, which `VolcanismStage` guarantees, and the per-vent roll keys on it. A gap would
 * waste a stream and a duplicate would make two volcanoes erupt in lockstep for the life of the world.
 */
internal class VentFacts(
  val index: Int,
  val position: Vec2d,
  /** 0 to 1: how vigorous the vent is. Scales its eruption rate, nothing else. */
  val strength: Double
)

internal class HistorySim(
  private val params: HistoryParams,
  private val facts: List<SiteFacts>,
  /**
   * The volcanoes, from `VolcanismStage`. Empty on a world with no convergent boundary and no hotspot on land,
   * which is a legitimate seed - and then nothing erupts, which is the right answer rather than a special case.
   */
  private val vents: List<VentFacts>,
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

  /** Cave systems that already hold a hoard. One quest per room. */
  private val usedCaves = HashSet<Int>()
  private val sites = ArrayList<Site>()
  private val wars = ArrayList<War>()

  /** Site indices of the wounds, in the order the star broke. Empty until it falls. */
  private val wounds = ArrayList<Int>()

  /** Event id of the fall, or -1 while it has not happened. Every mana event cites it. */
  private var starFell = -1

  /**
   * Settlements anywhere in the world given up to the blight, running total.
   *
   * World-wide rather than per civ because it answers a question Chaos asks about the *world*: has anything at
   * all ended since we swore. A per-civ count would have a Chaos people waiting for its own towns to fall,
   * which is not what they are waiting for.
   */
  private var blightLosses = 0

  private val dynasties = ArrayList<Dynasty>()
  private val deities = ArrayList<Deity>()
  private val cults = ArrayList<Cult>()
  private val monsters = ArrayList<Monster>()

  /** Whether history has already raised the one god it may add to the first ones. */
  private var lateGodRisen = false

  /** Events up to here have been looked at for a reason to raise a late god. */
  private var eventsScannedForGods = 0
  private val relations = ArrayList<RelationRecord>()

  /** Vengeance sworn and not yet taken. */
  private val oaths = ArrayList<Oath>()

  private val events = ArrayList<HistoryEvent>()
  private var prunedEvents = 0
  private var nextEventId = 0

  /** Grows with contact and resets when war is declared. */
  private val hostility = Hostility()

  /** Straight-line distances between every pair of sites, in metres. */
  private val distance: Array<DoubleArray> = Array(facts.size) { a ->
    DoubleArray(facts.size) { b -> facts[a].position.distanceTo(facts[b].position) }
  }

  private val startYear = 1
  private val presentYear = params.years

  init {
    // A ward that can never be raised is a subsystem that ships dead, and the two numbers live in different
    // places - one is a designer's tunable and the other is a rate constant - so nothing but this keeps them
    // in the right order.
    require(params.blightsBeforeForsaking > WARD_AFTER_BLIGHTS) {
      "blightsBeforeForsaking ${params.blightsBeforeForsaking} must exceed WARD_AFTER_BLIGHTS " +
          "$WARD_AFTER_BLIGHTS, or a town is forsaken before it can ward itself and no ward is ever raised"
    }
  }

  fun run(): Chronicle {
    createPantheon()
    scheduleMonsters()
    seedCivilisations()

    var year = startYear
    while (year <= presentYear) {
      for (civ in civs.toList()) {
        if (!civ.exists) continue
        growPopulations(civ, year)
        expandOrMigrate(civ, year)
        advanceTechnology(civ, year)
      }
      // Before relations, so a new people is in contact from its first tick.
      arriveOverSea(year)
      migrateOverland(year)
      secede(year)
      updateRelations(year)
      arrangeMarriages(year)
      resolveWars(year)
      resolveDisasters(year)
      // After the disasters, so a town already emptied this tick is not buried a second time - `abandon` returns
      // early on a town that is not standing, so the order is belt and braces rather than load-bearing.
      resolveEruptions(year)
      resolveMana(year)
      // After the mana, so an oath taken this tick can be a reaction to the blight that landed in it, and
      // before the figures, so a person born into a sworn people inherits their Order rather than their
      // grandparents'. Returns immediately on a world where the Orders play no part.
      swearOrders(year)
      updateFigures(year)
      // After the figures, so a prophet who rose this tick can found a cult in it.
      foundCults(year)
      spreadCults(year)
      persecuteCults(year)
      absorbCults(year)
      raiseLateGod(year)
      resolveMonsters(year)
      updateArtifacts(year)
      vanishSeers(year)
      // After the seers, so a prophet who has already walked out to a wound this tick is not also lost in a
      // desert. Both passes skip a figure whose `death` is set, so the order is what makes that exclusion
      // deterministic rather than a race between two rolls on the same person.
      loseTravellers(year)
      raiseMonuments(year)
      buildSites(year)
      // Beside the other built sites and after them, so `siteSeparation` sees a mine or a fort that went up
      // this same tick rather than putting a shrine on top of one.
      raiseShrines(year)
      buildWalls(year)
      retireCivs(year)

      year += params.yearsPerTick
    }

    // A beast whose reign outlasts the history ends where the history ends, so every story has its fate.
    for (monster in monsters.filter { it.awoke != 0 && it.fate == null }) resolveFate(monster, presentYear)
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

      seedCiv(site)
    }

    // A world too small for a city gets one civ anyway, seeded on its best site - otherwise nothing has a
    // history at all, which is a worse answer than a small one.
    if (civs.isEmpty() && facts.isNotEmpty()) seedCiv(facts.first())

    // One people means no wars, which on a genesis-size world was every seed.
    if (civs.size < 2) {
      val rival = facts.filter { towns[it.index].founded == 0 }.maxByOrNull { rivalScore(it) }
      if (rival != null) seedCiv(rival)
    }
  }

  private fun seedCiv(site: SiteFacts) {
    val town = towns[site.index]
    val civ = addCiv(site.cultureIndex, town, startYear, CivOrigin.SEEDED)
    joinCiv(civ, town, startYear)
    civ.foundedEvent = log(
      startYear, EventKind.CIV_FOUNDED,
      listOf(Actor(ActorType.CIV, civ.index), Actor(ActorType.SETTLEMENT, site.index)),
      site.position, emptyList(),
      "the ${civName(civ)} settle at ${nameOf(town)}"
    )
  }

  /** Far from every capital first, then a different culture, then a bigger site. */
  private fun rivalScore(site: SiteFacts): Double {
    val nearestCapital = civs.minOf { distance[site.index][it.capital] }
    val newCulture = if (civs.none { it.cultureIndex == site.cultureIndex }) RIVAL_NEW_CULTURE_BONUS else 1.0
    val tier = 1.0 / (1.0 + RIVAL_TIER_PENALTY * site.tier.ordinal)
    return nearestCapital * newCulture * tier
  }

  /**
   * The one way a civ enters the history, at the start or later. Safe to call inside a civ loop, because those
   * loops iterate `civs.toList()`. The caller settles or hands over the capital and logs the founding: only the
   * caller knows how the people came to be.
   */
  private fun addCiv(cultureIndex: Int, capital: Town, year: Int, origin: CivOrigin, parent: Int = -1): Civ {
    val civ = Civ(civs.size, cultureIndex, capital.facts.index, origin, parent)
    civ.nameSeed = Names.seedOf(worldSeed, CIV_NAME_SALT, civ.index.toLong())
    civ.founded = year
    civs.add(civ)
    hostility.addCiv()
    // A people that broke away from another keeps its gods.
    civ.patron = civs.getOrNull(parent)?.patron ?: patronFor(civ)
    return civ
  }

  /** A standing town changes hands, by conquest or by breaking away. */
  private fun transferTown(town: Town, from: Civ, to: Civ) {
    from.towns.remove(town.facts.index)
    to.towns.add(town.facts.index)
    town.owner = to.index
  }

  /** A people whose capital fell or emptied rules from its largest standing town. */
  private fun relocateCapital(civ: Civ) {
    val capital = towns[civ.capital]
    if (capital.standing && capital.owner == civ.index) return
    civ.capital = seatOf(civ) ?: return
  }

  private fun joinCiv(civ: Civ, town: Town, year: Int) {
    town.founded = year
    town.owner = civ.index
    town.foundingCiv = civ.index
    town.population = max(1.0, town.facts.potential * INITIAL_OCCUPANCY)
    town.wealth = (0.25 + town.facts.resourceValue * 0.35 + town.facts.habitability * 0.3)
      .coerceIn(0.0, 1.0)
    town.nameSeed = Names.seedOf(worldSeed, PLACE_NAME_SALT, town.facts.index.toLong())
    town.patron = civ.patron
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

  // --- New peoples -----------------------------------------------------------------------------------

  /** How many times the reference world's settlement sites this world has. See [HistoryParams.referenceSites]. */
  private val sizeScale = facts.size / params.referenceSites

  private fun arriveOverSea(year: Int) {
    val site = newcomerSite(year, params.arrivalChance, params.arrivalFrom, params.arrivalTo, ARRIVAL_SALT) {
      it.coastal
    } ?: return

    val town = towns[site]
    val civ = settleNewcomers(town, year, Culture.SEAFARING, CivOrigin.ARRIVED)
    civ.foundedEvent = log(
      year, EventKind.CIV_ARRIVED,
      listOf(Actor(ActorType.CIV, civ.index), Actor(ActorType.SETTLEMENT, site)),
      town.facts.position, emptyList(),
      "the ${civName(civ)} come over the sea and settle at ${nameOf(town)}"
    )
  }

  private fun migrateOverland(year: Int) {
    val site = newcomerSite(year, params.migrationChance, params.migrationFrom, params.migrationTo, MIGRATION_SALT) {
      !it.coastal
    } ?: return

    val town = towns[site]
    val civ = settleNewcomers(town, year, Culture.PASTORAL, CivOrigin.MIGRATED)
    civ.foundedEvent = log(
      year, EventKind.CIV_MIGRATED,
      listOf(Actor(ActorType.CIV, civ.index), Actor(ActorType.SETTLEMENT, site)),
      town.facts.position, emptyList(),
      "the ${civName(civ)} come out of the far country and settle at ${nameOf(town)}"
    )
  }

  /**
   * Where newcomers settle this tick, or null when they do not come. They come at an expected [chance] times per
   * reference world inside the window, and take the empty site farthest from every standing town.
   */
  private fun newcomerSite(
    year: Int,
    chance: Double,
    from: Int,
    to: Int,
    salt: Long,
    accepts: (SiteFacts) -> Boolean
  ): Int? {
    if (year !in from..to) return null

    val ticks = max(1, (to - from) / params.yearsPerTick)
    val perTick = (chance * sizeScale / ticks).coerceAtMost(1.0)
    if (roll(year.toLong(), salt) >= perTick) return null

    return remotestEmptySite(accepts)
  }

  /** The empty site farthest from every standing town, at least [HistoryParams.newcomerClearance] from any. */
  private fun remotestEmptySite(accepts: (SiteFacts) -> Boolean): Int? {
    return facts
      .filter { towns[it.index].founded == 0 && it.habitability >= params.minFoundingHabitability && accepts(it) }
      .map { it.index to distanceToStandingTown(it.index) }
      .filter { (_, gap) -> gap >= params.newcomerClearance }
      .maxByOrNull { (_, gap) -> gap }
      ?.first
  }

  private fun distanceToStandingTown(site: Int): Double {
    return towns.filter { it.standing }.minOfOrNull { distance[site][it.facts.index] } ?: Double.MAX_VALUE
  }

  /** Newcomers bring a culture the world has not seen yet, when there is one, and the world's know-how. */
  private fun settleNewcomers(town: Town, year: Int, fallback: Culture, origin: CivOrigin): Civ {
    val unseen = Culture.ALL.indices.filter { culture -> civs.none { it.cultureIndex == culture } }
    val culture = if (unseen.isEmpty()) {
      Culture.indexOf(fallback)
    } else {
      unseen[(roll(year.toLong(), town.facts.index.toLong(), NEWCOMER_CULTURE_SALT) * unseen.size).toInt()]
    }

    val technology = civs.filter { it.exists }.map { it.technology }.average().takeIf { !it.isNaN() } ?: 0.0
    val civ = addCiv(culture, town, year, origin)
    joinCiv(civ, town, year)
    civ.technology = technology
    return civ
  }

  /** A large civ's most aggrieved far town may rise. A won revolt takes the towns nearer to it than to the capital. */
  private fun secede(year: Int) {
    for (parent in civs.toList()) {
      if (!parent.exists) continue

      val standing = parent.towns.filter { towns[it].standing }
      if (standing.size < params.secessionMinTowns) continue

      val leader = standing.filter { it != parent.capital }.maxByOrNull { grievanceOf(parent, towns[it]) } ?: continue
      val grievance = grievanceOf(parent, towns[leader])
      if (roll(year.toLong(), parent.index.toLong(), REVOLT_SALT) >= params.revoltRate * grievance) continue

      revolt(parent, towns[leader], year)
    }
  }

  /** 0 to 1. Half of it is being beyond the capital's reach; sackings and blight make up the rest. */
  private fun grievanceOf(parent: Civ, town: Town): Double {
    val reach = distance[town.facts.index][parent.capital] / parent.culture.expansionRange
    val cutOff = (reach - 1.0).coerceIn(0.0, 1.0) * 0.5
    val sacked = min(town.sacked, 2) * 0.25
    val blighted = if (town.blights > 0) 0.25 else 0.0
    return (cutOff + sacked + blighted).coerceAtMost(1.0)
  }

  private fun revolt(parent: Civ, leader: Town, year: Int, causes: List<Int> = emptyList(), certain: Boolean = false) {
    val actors = listOf(Actor(ActorType.SETTLEMENT, leader.facts.index), Actor(ActorType.CIV, parent.index))
    val wins = certain || roll(year.toLong(), leader.facts.index.toLong(), REVOLT_OUTCOME_SALT) < params.revoltSuccess
    if (!wins) {
      leader.population *= 1.0 - REVOLT_POPULATION_LOSS
      log(
        year, EventKind.CIV_REVOLT, actors, leader.facts.position, causes,
        "${nameOf(leader)} rises against the ${civName(parent)} and is put down"
      )
      return
    }

    val uprising = log(
      year, EventKind.CIV_REVOLT, actors, leader.facts.position, causes,
      "${nameOf(leader)} rises against the ${civName(parent)}"
    )

    val followers = parent.towns.filter {
      it != parent.capital && towns[it].standing && distance[it][leader.facts.index] < distance[it][parent.capital]
    }
    val civ = addCiv(parent.cultureIndex, leader, year, CivOrigin.SECEDED, parent.index)
    civ.technology = parent.technology
    for (index in followers) transferTown(towns[index], parent, civ)
    hostility.set(parent.index, civ.index, params.secessionHostility)

    val others = followers.size - 1
    val who = if (others == 0) "${nameOf(leader)} breaks" else "${nameOf(leader)} and $others other towns break"
    val seceded = log(
      year, EventKind.CIV_SECEDED,
      listOf(Actor(ActorType.CIV, civ.index), Actor(ActorType.CIV, parent.index),
        Actor(ActorType.SETTLEMENT, leader.facts.index)),
      leader.facts.position, listOf(uprising),
      "$who away from the ${civName(parent)} as the ${civName(civ)}"
    )
    civ.foundedEvent = seceded
    parent.grudges.add(civ.index to seceded)
    civ.grudges.add(parent.index to seceded)
  }

  /** Survivors of a big town lost to war, ash or blight may found a people of their own nearby. */
  private fun foundRefuge(lost: Town, survivors: Double, lostOwner: Int, year: Int, ending: Int) {
    if (survivors < params.refugeeMinPopulation) return
    if (roll(year.toLong(), lost.facts.index.toLong(), REFUGEE_SALT) >= params.refugeeChance) return

    val site = facts
      .filter { towns[it.index].founded == 0 && it.habitability >= params.minFoundingHabitability }
      .filter { distance[it.index][lost.facts.index] <= params.refugeeRange }
      .maxByOrNull { it.habitability * (1.0 - distance[it.index][lost.facts.index] / params.refugeeRange) }
      ?: return

    val town = towns[site.index]
    val formerCiv = civs.getOrNull(lostOwner)
    val civ = addCiv(formerCiv?.cultureIndex ?: lost.facts.cultureIndex, town, year, CivOrigin.REFOUNDED, lostOwner)
    joinCiv(civ, town, year)
    civ.technology = formerCiv?.technology ?: 0.0
    civ.foundedEvent = log(
      year, EventKind.CIV_REFOUNDED,
      listOf(Actor(ActorType.CIV, civ.index), Actor(ActorType.SETTLEMENT, site.index)),
      town.facts.position, listOf(ending),
      "survivors of ${nameOf(lost)} found the ${civName(civ)} at ${nameOf(town)}"
    )
  }

  // --- Gods and cults --------------------------------------------------------------------------------

  /**
   * The first gods, drawn from the domain catalogue without repeats. How many grows with the world; which ones
   * follows the land: coasts call for a god of the sea, rich soil for one of the harvest, mana and volcanoes for
   * death, healing and fate, warlike cities for war.
   */
  private fun createPantheon() {
    if (facts.isEmpty()) return

    val doublings = Math.log(max(sizeScale, MIN_PANTHEON_SCALE)) / Math.log(2.0)
    val extra = if (roll(DEITY_EXTRA_SALT) < 0.5) 1 else 0
    val count = (Math.round(params.deitiesAtReference + params.deitiesPerDoubling * doublings).toInt() + extra)
      .coerceIn(MIN_DEITIES, min(params.maxDeities, Domain.entries.size))

    val weights = pantheonWeights()
    repeat(count) { draw ->
      val left = weights.filterKeys { domain -> deities.none { it.domain == domain } }
      val domain = pickWeighted(left, roll(draw.toLong(), DEITY_DRAW_SALT)) ?: return
      addDeity(domain, startYear, cause = -1)
    }
  }

  private fun pantheonWeights(): Map<Domain, Double> {
    val coast = facts.count { it.coastal }.toDouble() / facts.size
    val soil = facts.map { it.fertility }.average()
    val unrest = facts.map { it.volcanism + it.mana }.average()
    val wealth = facts.map { it.resourceValue }.average()
    val war = facts.filter { it.tier == SettlementTier.CITY }.map { it.culture.bellicosity }.average()
      .takeIf { !it.isNaN() } ?: 0.0

    return Domain.entries.associateWith { domain ->
      when (domain) {
        Domain.SEA -> 1.0 + 4.0 * coast
        Domain.HARVEST -> 1.0 + 3.0 * soil
        Domain.DEATH, Domain.HEALING, Domain.FATE -> 1.0 + 2.0 * unrest
        Domain.WAR -> 1.0 + 3.0 * war
        Domain.CRAFT -> 1.0 + 3.0 * wealth
        else -> 1.0
      }
    }
  }

  private fun addDeity(domain: Domain, year: Int, cause: Int): Deity {
    val nameSeed = Names.seedOf(worldSeed, DEITY_NAME_SALT, deities.size.toLong())
    val deity = Deity(deities.size, domain, nameSeed, year, cause)
    deities.add(deity)
    return deity
  }

  /** A people's patron: any of the world's gods, the ones that suit how the people live more often. */
  private fun patronFor(civ: Civ): Int {
    if (deities.isEmpty()) return -1
    val culture = civ.culture
    val weights = deities.associate { deity ->
      deity.index to when (deity.domain) {
        Domain.WAR -> 1.0 + 3.0 * culture.bellicosity
        Domain.SEA -> 1.0 + 3.0 * culture.harbour
        Domain.CRAFT -> 1.0 + 3.0 * culture.craftBias
        Domain.WISDOM -> 1.0 + 3.0 * culture.inventiveness
        Domain.DEATH, Domain.FATE -> 1.0 + 2.0 * culture.clergyBias
        else -> 1.0
      }
    }
    return pickWeighted(weights, roll(civ.index.toLong(), PATRON_SALT)) ?: -1
  }

  /**
   * History may call one more god into worship: the first plague turns people to healing, the first town lost
   * to the blight to death. Only one, and only for a domain the world has no god of yet.
   */
  private fun raiseLateGod(year: Int) {
    if (lateGodRisen || deities.isEmpty()) {
      eventsScannedForGods = events.size
      return
    }
    val fresh = events.subList(eventsScannedForGods, events.size).toList()
    eventsScannedForGods = events.size

    for (event in fresh) {
      val domain = when (event.kind) {
        EventKind.PLAGUE -> Domain.HEALING
        EventKind.SETTLEMENT_FORSAKEN -> Domain.DEATH
        else -> continue
      }
      if (deities.any { it.domain == domain }) continue

      val deity = addDeity(domain, year, event.id)
      lateGodRisen = true
      val place = event.actors.firstOrNull { it.type == ActorType.SETTLEMENT }?.index
      val culture = place?.let { towns[it].facts.cultureIndex } ?: facts.first().cultureIndex
      log(
        year, EventKind.DEITY_RISEN,
        listOfNotNull(Actor(ActorType.DEITY, deity.index), place?.let { Actor(ActorType.SETTLEMENT, it) }),
        event.where, listOf(event.id),
        "after ${event.detail}, people turn to ${Names.deity(deity.nameSeed, culture, domain)}"
      )
      return
    }
  }

  /** A living prophet may found a cult of a god, of a kind of beast, or of the star that fell. */
  private fun foundCults(year: Int) {
    for (civ in civs.toList()) {
      if (!civ.exists) continue
      val prophet = people.firstOrNull {
        it.death == 0 && it.civ == civ.index && it.role == FigureRole.PROPHET && towns[it.home].standing &&
            cults.none { cult -> cult.founder == it.index } &&
            roll(year.toLong(), it.index.toLong(), CULT_SALT) < params.cultChance
      } ?: continue
      foundCult(prophet, civ, year)
    }
  }

  private fun foundCult(prophet: Person, civ: Civ, year: Int) {
    val home = towns[prophet.home]
    val others = deities.filter { it.index != civ.patron }
    val weights = buildMap {
      if (others.isNotEmpty()) put(CultKind.DEITY, 2.0)
      put(CultKind.BEAST, 1.0)
      if (starFell >= 0) put(CultKind.STAR, 1.0 + 3.0 * home.facts.mana)
    }
    val kind = pickWeighted(weights, roll(year.toLong(), prophet.index.toLong(), CULT_KIND_SALT)) ?: return
    val pick = roll(year.toLong(), prophet.index.toLong(), CULT_PICK_SALT)
    val deity = if (kind == CultKind.DEITY) others[(pick * others.size).toInt()].index else -1
    val beast = if (kind == CultKind.BEAST) BeastKind.entries[(pick * BeastKind.entries.size).toInt()] else null
    val leaning = when (kind) {
      CultKind.DEITY -> deities[deity].domain.leaning
      CultKind.STAR -> Faction.CHAOS
      CultKind.BEAST -> Faction.CIRCLE
    }

    val index = cults.size
    val sanctuary = addSite(
      SiteKind.SANCTUARY, offset(home.facts.position, SANCTUARY_KEY + index, SANCTUARY_OFFSET), year,
      home.facts.index, civ.index, SANCTUARY_RADIUS, artifact = -1, figure = prophet.index, cult = index
    )
    home.sites.add(sanctuary)
    val cult = Cult(
      index, Names.seedOf(worldSeed, CULT_NAME_SALT, index.toLong()), kind, deity, beast, prophet.index,
      home.facts.index, year, leaning, sanctuary
    )
    cult.towns.add(home.facts.index)
    cults.add(cult)

    log(
      year, EventKind.CULT_FOUNDED,
      listOfNotNull(
        Actor(ActorType.FIGURE, prophet.index), Actor(ActorType.CULT, index),
        deity.takeIf { it >= 0 }?.let { Actor(ActorType.DEITY, it) },
        Actor(ActorType.SETTLEMENT, home.facts.index), Actor(ActorType.CIV, civ.index)
      ),
      home.facts.position, emptyList(),
      "${personName(prophet)} founds ${cultName(cult)} at ${nameOf(home)} and raises ${siteName(sites[sanctuary])}"
    )
  }

  /** A cult wins over the nearest town not yet its own, from any town that keeps it. */
  private fun spreadCults(year: Int) {
    for (cult in cults.filter { it.active }) {
      if (roll(year.toLong(), cult.index.toLong(), CULT_SPREAD_SALT) >= params.cultSpreadChance) continue

      val keeping = cult.towns.filter { towns[it].standing }
      val next = towns.filter { it.standing && it.facts.index !in cult.towns }
        .map { town -> town to keeping.minOfOrNull { distance[it][town.facts.index] } }
        .filter { (_, gap) -> gap != null && gap <= params.cultSpreadRange }
        .minByOrNull { (_, gap) -> gap!! }
        ?.first ?: continue

      cult.towns.add(next.facts.index)
      log(
        year, EventKind.CULT_SPREAD,
        listOf(Actor(ActorType.CULT, cult.index), Actor(ActorType.SETTLEMENT, next.facts.index)),
        next.facts.position, emptyList(),
        "${cultName(cult)} win followers in ${nameOf(next)}"
      )
    }
  }

  /**
   * The people that holds most of a cult's towns may turn on it. The cult is wiped out, rises up and takes
   * the towns that follow it as a realm of its own, or endures.
   */
  private fun persecuteCults(year: Int) {
    for (cult in cults.filter { it.active }) {
      val held = cult.towns.filter { towns[it].standing && towns[it].owner >= 0 }
      val persecutor = held.groupingBy { towns[it].owner }.eachCount().maxByOrNull { it.value }?.key ?: continue
      if (roll(year.toLong(), cult.index.toLong(), CULT_PERSECUTION_SALT) >= params.cultPersecutionChance) continue

      val by = civs[persecutor]
      val persecution = log(
        year, EventKind.CULT_PERSECUTED,
        listOf(Actor(ActorType.CULT, cult.index), Actor(ActorType.CIV, persecutor)),
        null, emptyList(),
        "the ${civName(by)} turn on ${cultName(cult)}"
      )

      val outcome = roll(year.toLong(), cult.index.toLong(), CULT_OUTCOME_SALT)
      when {
        outcome < params.cultWipeShare -> {
          cult.fate = CultFate.WIPED_OUT
          cult.ended = year
          log(
            year, EventKind.CULT_WIPED_OUT,
            listOf(Actor(ActorType.CULT, cult.index), Actor(ActorType.CIV, persecutor)),
            null, listOf(persecution),
            "the ${civName(by)} stamp out ${cultName(cult)}"
          )
        }
        outcome < params.cultWipeShare + params.cultRevoltShare -> riseAsRealm(cult, by, year, persecution)
      }
    }
  }

  /** The cult's towns under the persecutor break away, led by the cult's first town if it is among them. */
  private fun riseAsRealm(cult: Cult, from: Civ, year: Int, persecution: Int) {
    val followers = cult.towns.filter { towns[it].standing && towns[it].owner == from.index && it != from.capital }
    if (followers.isEmpty()) return
    val leader = towns[if (cult.origin in followers) cult.origin else followers.first()]

    val realm = addCiv(from.cultureIndex, leader, year, CivOrigin.CULT_REALM, from.index)
    realm.technology = from.technology
    if (cult.deity >= 0) realm.patron = cult.deity
    for (index in followers) transferTown(towns[index], from, realm)
    hostility.set(from.index, realm.index, params.secessionHostility)
    cult.fate = CultFate.REALM
    cult.realm = realm.index

    realm.foundedEvent = log(
      year, EventKind.CULT_REVOLT,
      listOf(
        Actor(ActorType.CULT, cult.index), Actor(ActorType.CIV, realm.index), Actor(ActorType.CIV, from.index),
        Actor(ActorType.SETTLEMENT, leader.facts.index)
      ),
      leader.facts.position, listOf(persecution),
      "${cultName(cult)} rise against the ${civName(from)} at ${nameOf(leader)} and rule as the ${civName(realm)}"
    )
    from.grudges.add(realm.index to realm.foundedEvent)
    realm.grudges.add(from.index to realm.foundedEvent)
  }

  /** On a world where the Orders are known, a cult may join the Order its teaching leans toward. */
  private fun absorbCults(year: Int) {
    if (params.orderInfluence.isAbsent) return

    for (cult in cults.filter { it.active && it.leaning != null }) {
      val order = civs.firstOrNull { it.exists && it.sworn == cult.leaning } ?: continue
      if (roll(year.toLong(), cult.index.toLong(), CULT_ABSORB_SALT) >= params.cultAbsorbChance) continue

      cult.fate = CultFate.ABSORBED
      cult.ended = year
      log(
        year, EventKind.CULT_ABSORBED,
        listOf(Actor(ActorType.CULT, cult.index), Actor(ActorType.CIV, order.index)),
        null, listOfNotNull(order.swornEvent.takeIf { it >= 0 }),
        "${cultName(cult)} join ${cult.leaning!!.label}"
      )
    }
  }

  private fun cultName(cult: Cult): String {
    val culture = towns[cult.origin].facts.cultureIndex
    val deityName = deities.getOrNull(cult.deity)?.let { Names.deity(it.nameSeed, culture, it.domain) }
    return Names.cult(cult.nameSeed, cult.kind, deityName, cult.beast)
  }

  /** One key of a weighted map, chosen by [unit] in [0,1), or null for an empty map. */
  private fun <K> pickWeighted(weights: Map<K, Double>, unit: Double): K? {
    var left = unit * weights.values.sum()
    for ((key, weight) in weights) {
      left -= weight
      if (left <= 0.0) return key
    }
    return weights.keys.lastOrNull()
  }

  // --- World bosses ----------------------------------------------------------------------------------

  /**
   * Which world bosses this history wakes, and when. Two on a reference world, one more per doubling of its sites,
   * and a coin flip more, up to the catalogue. A boss whose kind of lair this world lacks (no cave, no deep sea)
   * is skipped.
   */
  private fun scheduleMonsters() {
    if (facts.isEmpty()) return

    val doublings = Math.log(max(sizeScale, 1.0)) / Math.log(2.0)
    val extra = if (roll(MONSTER_EXTRA_SALT) < 0.5) 1 else 0
    val count = (Math.round(params.monstersAtReference + params.monstersPerDoubling * doublings).toInt() + extra)
      .coerceIn(0, min(params.maxMonsters, WorldBoss.entries.size))

    val bosses = WorldBoss.entries
      .sortedBy { roll(it.ordinal.toLong(), MONSTER_ORDER_SALT) }
      .filter { lairCandidates(it.lair).isNotEmpty() }
      .take(count)
    for (boss in bosses) {
      val index = monsters.size
      val awakens = params.monsterFrom +
          (roll(index.toLong(), MONSTER_WAKE_SALT) * (params.monsterTo - params.monsterFrom)).toInt()
      monsters.add(Monster(index, boss, Names.seedOf(worldSeed, MONSTER_NAME_SALT, index.toLong()), awakens))
    }
  }

  private fun lairCandidates(lair: LairKind): List<SiteCandidate> = when (lair) {
    LairKind.CAVE -> candidates.caves
    LairKind.REMOTE_LAND -> candidates.remote
    LairKind.OCEAN -> candidates.deeps
  }

  private fun resolveMonsters(year: Int) {
    for (monster in monsters) {
      when {
        monster.fate != null -> continue
        monster.awoke == 0 -> if (year >= monster.awakens) awaken(monster, year)
        year >= monster.resolves -> resolveFate(monster, year)
        else -> ravage(monster, year)
      }
    }
  }

  /** The beast wakes out of whatever this history gave it, the most fitting past its species has, or out of nowhere. */
  private fun awaken(monster: Monster, year: Int) {
    val name = monsterName(monster)
    var site: Int? = null
    val detail = monster.boss.origins.firstNotNullOfOrNull { origin ->
      wakeFrom(origin, name, monster.index)?.let { origin to it }
    }
    if (detail != null) {
      val (origin, wake) = detail
      monster.origin = origin
      monster.originEvent = wake.cause
      monster.position = wake.position
      site = wake.site
    } else {
      monster.position = lairCandidates(monster.boss.lair).first().position
    }

    monster.awoke = year
    monster.resolves = year + params.monsterReignMin +
        (roll(monster.index.toLong(), MONSTER_REIGN_SALT) * (params.monsterReignMax - params.monsterReignMin)).toInt()
    monster.awokeEvent = log(
      year, EventKind.MONSTER_AWOKE,
      listOfNotNull(Actor(ActorType.MONSTER, monster.index), site?.let { Actor(ActorType.SITE, it) }),
      monster.position, listOfNotNull(monster.originEvent.takeIf { it >= 0 }),
      detail?.second?.text ?: "$name wakes in the wilds"
    )
  }

  private class Wake(val position: Vec2d, val site: Int?, val cause: Int, val text: String)

  /** How this origin could wake the beast in this history so far, or null when the history holds no such past. */
  private fun wakeFrom(origin: MonsterOrigin, name: String, index: Int): Wake? = when (origin) {
    // One wound per beast where there are several, so two beasts do not crawl out of the same crater.
    MonsterOrigin.WOUND -> sites.filter { it.kind == SiteKind.WOUND }.takeIf { it.isNotEmpty() }?.let { wounds ->
      val wound = wounds[index % wounds.size]
      Wake(wound.position, wound.index, starFell, "$name rises out of ${siteName(wound)}")
    }
    MonsterOrigin.ROBBED_TOMB -> sites.lastOrNull { it.kind == SiteKind.TOMB }?.let {
      Wake(it.position, it.index, -1, "grave robbers break into ${siteName(it)} and wake $name")
    }
    MonsterOrigin.ERUPTION -> events.lastOrNull { it.kind == EventKind.ERUPTION && it.where != null }?.let {
      Wake(it.where!!, null, it.id, "$name crawls out of the ash after ${it.detail}")
    }
    MonsterOrigin.SUMMONED -> cults.lastOrNull()?.let { cult ->
      val founding = events.lastOrNull { e ->
        e.kind == EventKind.CULT_FOUNDED && Actor(ActorType.CULT, cult.index) in e.actors
      }
      Wake(sites[cult.sanctuary].position, cult.sanctuary, founding?.id ?: -1, "${cultName(cult)} call up $name")
    }
    MonsterOrigin.RISEN_DEAD -> sites.filter { it.kind == SiteKind.BATTLEFIELD && it.fallen >= params.risenDeadFallen }
      .maxByOrNull { it.fallen }?.let { field ->
        val battle = events.lastOrNull { e ->
          e.kind == EventKind.BATTLE && Actor(ActorType.SITE, field.index) in e.actors
        }
        Wake(field.position, field.index, battle?.id ?: -1, "the dead of ${siteName(field)} rise as $name")
      }
    MonsterOrigin.ANCIENT -> null
  }

  /** The beast falls on the nearest standing town within reach, which loses a share of its people. */
  private fun ravage(monster: Monster, year: Int) {
    if (roll(year.toLong(), monster.index.toLong(), RAVAGE_SALT) >= params.ravageChance) return
    val town = towns.filter { it.standing && it.facts.position.distanceTo(monster.position) <= params.ravageRange }
      .minByOrNull { it.facts.position.distanceTo(monster.position) } ?: return

    town.population = max(8.0, town.population * (1.0 - params.ravageLoss))
    log(
      year, EventKind.MONSTER_RAVAGED,
      listOf(Actor(ActorType.MONSTER, monster.index), Actor(ActorType.SETTLEMENT, town.facts.index)),
      town.facts.position, listOf(monster.awokeEvent),
      "${monsterName(monster)} falls on ${nameOf(town)}"
    )
  }

  /**
   * How the story ends: slain by a hero, sealed in a crystal, withdrawn to its lair, or vanished. The last beast
   * to meet its end is never slain when every one before it was, so a world always keeps one alive.
   */
  private fun resolveFate(monster: Monster, year: Int) {
    val noneAlive = monsters.none { it.fate != null && it.fate != MonsterFate.SLAIN }
    val lastOne = monsters.count { it.fate == null } == 1
    val weights = buildMap {
      if (!(noneAlive && lastOne)) put(MonsterFate.SLAIN, params.slainWeight)
      put(MonsterFate.SEALED, params.sealedWeight)
      put(MonsterFate.WITHDRAWN, params.withdrawnWeight)
      put(MonsterFate.VANISHED, params.vanishedWeight)
    }
    val fate = pickWeighted(weights, roll(monster.index.toLong(), MONSTER_FATE_SALT)) ?: MonsterFate.WITHDRAWN

    monster.ended = year
    val done = when (fate) {
      MonsterFate.SLAIN -> slay(monster, year)
      MonsterFate.SEALED -> false
      MonsterFate.WITHDRAWN, MonsterFate.VANISHED -> withdraw(monster, year, vanished = fate == MonsterFate.VANISHED)
    }
    if (!done) seal(monster, year)
  }

  /** A hero of the nearest people kills it and takes a trophy. Returns false when no people is there to fight it. */
  private fun slay(monster: Monster, year: Int): Boolean {
    val town = nearestHeldTown(monster.position) ?: return false
    val civ = civs[town.owner]
    val hero = people.firstOrNull {
      it.death == 0 && it.civ == civ.index && (it.role == FigureRole.GENERAL || it.role == FigureRole.RULER)
    } ?: newPerson(FigureRole.GENERAL, civ, town.facts.index, year)

    val field = addSite(
      SiteKind.BATTLEFIELD, monster.position, year, town.facts.index, civ.index, MONSTER_FIELD_RADIUS,
      artifact = -1, figure = hero.index, monster = monster.index
    )
    town.sites.add(field)
    val trophy = Relic(
      index = relics.size,
      nameSeed = Names.seedOf(worldSeed, ARTIFACT_NAME_SALT, relics.size.toLong()),
      kind = ArtifactKind.RELIQUARY,
      forgedYear = year,
      forgedBy = hero.index,
      material = "the bones of ${monster.boss.title.removePrefix("the ")}",
      forgedAtNameSeed = towns[hero.home].nameSeed,
      holder = hero.index
    )
    relics.add(trophy)

    val slain = log(
      year, EventKind.MONSTER_SLAIN,
      listOf(
        Actor(ActorType.FIGURE, hero.index), Actor(ActorType.MONSTER, monster.index), Actor(ActorType.SITE, field),
        Actor(ActorType.ARTIFACT, trophy.index)
      ),
      monster.position, listOf(monster.awokeEvent),
      "${personName(hero)} slays ${monsterName(monster)} near ${nameOf(town)} and keeps ${relicName(trophy)}"
    )
    trophy.provenance.add(slain)
    monster.fate = MonsterFate.SLAIN
    monster.hero = hero.index
    monster.site = field
    monster.trophy = trophy.index
    return true
  }

  /** A prophet or scholar of the nearest people binds it in a crystal, not far from where it raged. */
  private fun seal(monster: Monster, year: Int) {
    val town = nearestHeldTown(monster.position)
    val sealer = town?.let { near ->
      people.firstOrNull {
        it.death == 0 && it.civ == near.owner && (it.role == FigureRole.PROPHET || it.role == FigureRole.SCHOLAR)
      } ?: newPerson(FigureRole.SCHOLAR, civs[near.owner], near.facts.index, year)
    }

    val seal = addSite(
      SiteKind.SEAL, offset(monster.position, SEAL_KEY + monster.index, SEAL_OFFSET), year,
      town?.facts?.index ?: -1, town?.owner ?: -1, SEAL_RADIUS, artifact = -1, figure = sealer?.index ?: -1,
      monster = monster.index
    )
    town?.sites?.add(seal)
    monster.fate = MonsterFate.SEALED
    monster.hero = sealer?.index ?: -1
    monster.site = seal

    log(
      year, EventKind.MONSTER_SEALED,
      listOfNotNull(
        sealer?.let { Actor(ActorType.FIGURE, it.index) }, Actor(ActorType.MONSTER, monster.index),
        Actor(ActorType.SITE, seal)
      ),
      sites[seal].position, listOf(monster.awokeEvent),
      "${sealer?.let { personName(it) } ?: "nobody knows who"} binds ${monsterName(monster)} in crystal at " +
          siteName(sites[seal])
    )
  }

  /**
   * It withdraws to the lair its kind keeps: the far end of a cave, the remotest dry land, the deep sea. Returns
   * false when this world has no such place left.
   */
  private fun withdraw(monster: Monster, year: Int, vanished: Boolean): Boolean {
    val lair = when (monster.boss.lair) {
      LairKind.CAVE -> candidates.caves.firstOrNull { it.detail !in usedCaves }?.also { usedCaves.add(it.detail) }
      LairKind.REMOTE_LAND, LairKind.OCEAN -> lairCandidates(monster.boss.lair).firstOrNull { candidate ->
        sites.none { it.kind == SiteKind.LAIR && it.position.distanceTo(candidate.position) < params.siteSeparation }
      }
    } ?: return false

    val site = addSite(
      SiteKind.LAIR, lair.position, year, -1, -1, LAIR_RADIUS, artifact = -1, figure = -1,
      elevation = lair.elevation, monster = monster.index
    )
    monster.fate = if (vanished) MonsterFate.VANISHED else MonsterFate.WITHDRAWN
    monster.site = site

    if (vanished) {
      log(
        year, EventKind.MONSTER_VANISHED, listOf(Actor(ActorType.MONSTER, monster.index)), null,
        listOf(monster.awokeEvent), "${monsterName(monster)} vanishes, and nobody knows where it went"
      )
    } else {
      log(
        year, EventKind.MONSTER_WITHDREW,
        listOf(Actor(ActorType.MONSTER, monster.index), Actor(ActorType.SITE, site)),
        lair.position, listOf(monster.awokeEvent), "${monsterName(monster)} withdraws to ${siteName(sites[site])}"
      )
    }
    return true
  }

  private fun monsterName(monster: Monster) = Names.monster(monster.nameSeed, monster.boss)

  private fun nearestHeldTown(at: Vec2d): Town? {
    return towns.filter { it.standing && it.owner >= 0 }.minByOrNull { it.facts.position.distanceTo(at) }
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

        hostility.set(a, b, hostility.between(a, b) + gain)
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
        if (hostility.between(a, b) < params.warThreshold) continue
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
      "the ${civName(civs[a])} go to war with the ${civName(civs[b])}" + creedClause(civs[a], civs[b])
    )

    // The grudge points at the declaration, not at a number. An NPC can cite an event; it cannot cite a
    // hostility score - which is the difference between "they burned Ashford in 412" and "we dislike them".
    civs[a].grudges.add(b to cause)
    civs[b].grudges.add(a to cause)

    hostility.set(a, b, WAR_HOSTILITY_RESET)
    wars.add(War(a, b, year, cause))
  }

  /**
   * A clause naming the Orders when two belligerents hold opposing convictions, or the empty string.
   *
   * The cheapest hint in the whole subsystem: no new event kind, no new site, no roll, and it turns a line the
   * chronicle was already going to print into one that says *why*. Only Chaos against Eternity qualifies -
   * [Faction.opposite] returns null for the Circle, which is the design working rather than an omission: the
   * Circle's whole position is that neither rival is simply wrong, so a war it is in is a war about ground.
   *
   * Empty whenever either side is unaligned, which is every war on a world where the Orders play no part - so
   * no existing chronicle line moves.
   */
  private fun creedClause(a: Civ, b: Civ): String {
    val first = a.sworn ?: return ""
    val second = b.sworn ?: return ""
    if (first.opposite != second) return ""
    return ", ${first.label} against ${second.label}"
  }

  /**
   * One engagement per tick per war, at the frontier: the two nearest settlements of the two sides.
   *
   * The battle happens between them rather than at one of them, and that midpoint becomes a battlefield
   * site - which is why a world's battlefields end up strung along the borders that were contested rather
   * than scattered evenly, without anything having to model a border.
   */
  private fun fightBattle(war: War, year: Int) {
    val front = frontierOf(civs[war.a], civs[war.b])
    if (front.isEmpty()) {
      war.decided = true
      return
    }
    val (attacker, defender) = pickFront(front, war, year)

    val strengthA = strengthOf(civs[war.a])
    val strengthB = strengthOf(civs[war.b])
    val aWins = roll(year.toLong(), war.a.toLong(), war.b.toLong(), BATTLE_SALT) <
        strengthA / (strengthA + strengthB)

    val winner = if (aWins) war.a else war.b
    val loser = if (aWins) war.b else war.a
    val target = if (aWins) defender else attacker
    val from = towns[attacker].facts.position
    val to = towns[defender].facts.position
    val ground = battleGround(from, to, war, year)

    val casualties = ((strengthA + strengthB) * BATTLE_CASUALTY_SHARE).toInt().coerceAtLeast(20)
    val field = fieldAt(ground, from, to, war, year, target, winner)
    field.battles++
    field.fallen += casualties
    if (field.index !in towns[target].sites) towns[target].sites.add(field.index)

    val battle = log(
      year, EventKind.BATTLE,
      listOf(Actor(ActorType.CIV, winner), Actor(ActorType.CIV, loser), Actor(ActorType.SITE, field.index)),
      field.position, listOf(war.cause),
      "the ${civName(civs[winner])} defeat the ${civName(civs[loser])} near ${nameOf(towns[target])}, " +
          "$casualties dead"
    )

    // Losing a battle costs the loser's frontier settlement people whether or not it is then taken.
    towns[target].population = max(10.0, towns[target].population * (1.0 - BATTLE_POPULATION_LOSS))

    maybeFeud(winner, loser, year, battle, target)
    takeVengeance(winner, loser, year, battle, target)
    killGeneralsOf(loser, winner, year, battle, field.position)
    besiege(war, winner, loser, target, year, battle)
  }

  /**
   * The pairs of standing towns facing each other across the border, as (town of [a], town of [b]): each town
   * paired with its nearest enemy town, kept when that pair is no more than [HistoryParams.frontierSpread]
   * times the closest pair. As towns change hands the pairs change, which is what moves the front.
   */
  private fun frontierOf(a: Civ, b: Civ): List<Pair<Int, Int>> {
    val ofA = a.towns.filter { towns[it].standing }
    val ofB = b.towns.filter { towns[it].standing }
    if (ofA.isEmpty() || ofB.isEmpty()) return emptyList()

    val pairs = LinkedHashSet<Pair<Int, Int>>()
    for (i in ofA) pairs.add(i to ofB.minBy { distance[i][it] })
    for (j in ofB) pairs.add(ofA.minBy { distance[it][j] } to j)

    val closest = pairs.minOf { (i, j) -> distance[i][j] }
    return pairs.filter { (i, j) -> distance[i][j] <= closest * params.frontierSpread }
  }

  /** One pair of the front, the closer pairs more often. */
  private fun pickFront(front: List<Pair<Int, Int>>, war: War, year: Int): Pair<Int, Int> {
    val weights = front.map { (i, j) -> 1.0 / max(1.0, distance[i][j]) }
    var left = roll(year.toLong(), war.a.toLong(), war.b.toLong(), FRONT_SALT) * weights.sum()
    for ((index, weight) in weights.withIndex()) {
      left -= weight
      if (left <= 0.0) return front[index]
    }
    return front.last()
  }

  private fun distanceToSegment(point: Vec2d, from: Vec2d, to: Vec2d): Double {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0.0) return point.distanceTo(from)

    val t = (((point.x - from.x) * dx + (point.y - from.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
    return point.distanceTo(Vec2d(from.x + dx * t, from.y + dy * t))
  }

  /** Somewhere between the two towns, not always the midpoint: armies meet where they meet. */
  private fun battleGround(from: Vec2d, to: Vec2d, war: War, year: Int): Vec2d {
    val along = 0.5 + (roll(year.toLong(), war.a.toLong(), war.b.toLong(), GROUND_ALONG_SALT) - 0.5) * 0.4
    val point = from.lerp(to, along)

    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = max(1.0, Math.sqrt(dx * dx + dy * dy))
    val aside = (roll(year.toLong(), war.a.toLong(), war.b.toLong(), GROUND_ASIDE_SALT) - 0.5) * 2.0 *
        params.battleAside
    return Vec2d(point.x - dy / length * aside, point.y + dx / length * aside)
  }

  /**
   * Where the battle is fought: the battlefield within [HistoryParams.battleMergeRadius] of [ground]; else, now
   * and then, an old field on the way between the two towns, since armies come back to ground they know; else a
   * new field at [ground].
   */
  private fun fieldAt(ground: Vec2d, from: Vec2d, to: Vec2d, war: War, year: Int, target: Int, winner: Int): Site {
    val fields = sites.filter { it.kind == SiteKind.BATTLEFIELD }

    val near = fields.filter { it.position.distanceTo(ground) <= params.battleMergeRadius }
      .minByOrNull { it.position.distanceTo(ground) }
    if (near != null) return near

    val reach = params.battleAside + params.battleMergeRadius
    val onTheWay = fields.filter { distanceToSegment(it.position, from, to) <= reach }
      .minByOrNull { it.position.distanceTo(ground) }
    val returns = roll(year.toLong(), war.a.toLong(), war.b.toLong(), OLD_GROUND_SALT) < params.oldGroundChance
    if (onTheWay != null && returns) return onTheWay

    val index = addSite(
      SiteKind.BATTLEFIELD, ground, year, target, winner,
      radius = BATTLEFIELD_RADIUS, artifact = -1, figure = -1
    )
    return sites[index]
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

    if (betray(war, winner, loser, town, year, cause)) return

    town.sacked++
    val sack = log(
      year, EventKind.SETTLEMENT_SACKED,
      listOf(Actor(ActorType.SETTLEMENT, target), Actor(ActorType.CIV, winner), Actor(ActorType.CIV, loser)),
      town.facts.position, listOf(cause),
      "${nameOf(town)} is sacked by the ${civName(civs[winner])}"
    )
    town.population = max(8.0, town.population * (1.0 - SACK_POPULATION_LOSS))
    town.wealth *= 0.6
    hideHoard(town, target, year, sack)
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
      transferTown(town, civs[loser], civs[winner])
      relocateCapital(civs[loser])
      town.oldNameSeed = town.nameSeed
      town.nameSeed = Names.seedOf(worldSeed, PLACE_NAME_SALT, target.toLong(), year.toLong())

      val conquest = log(
        year, EventKind.CONQUEST,
        listOf(Actor(ActorType.SETTLEMENT, target), Actor(ActorType.CIV, winner)),
        town.facts.position, listOf(sack),
        "${nameOf(town)} passes to the ${civName(civs[winner])}, who rename it from " +
            Names.place(town.oldNameSeed, civs[loser].cultureIndex)
      )
      plunder(town, civs[winner], year, conquest)
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

      // The eruption roll used to be here, per town. It is now per vent - see [resolveEruptions].
    }
  }

  /**
   * The volcanoes wake, and whatever is downwind of one is buried.
   *
   * ### Why the roll moved from the town to the mountain
   *
   * It was `roll(year, townIndex) < town.volcanism * ERUPTION_RATE`, which is four separate things wrong and
   * every one of them invisible from a map:
   *
   * - **the chronicle had no line saying a mountain erupted.** The only event was the town's obituary, so an
   *   eruption was a way for a settlement to die rather than a thing that happened in the world.
   * - **two towns on one arc were buried in different centuries by different eruptions**, because each rolled
   *   its own. One eruption burying two neighbours - which is what an eruption does - was not expressible.
   * - **the event's `where` was the town**, so nothing tied it to a volcano and `Chronicle.provenanceOf` could
   *   not thread a ruin back to the mountain that made it.
   * - **an eruption always destroyed the town that rolled it**, which is what makes this pass the one that
   *   unlocks the lore query: no standing town had an eruption anywhere in its own history, so there was
   *   nothing for anybody to remember. Now a town inside [ASH_REACH] that survives its ashfall roll has one in
   *   living memory, and its buried neighbour is a walkable `ASH_RUIN` on the map.
   *
   * The two rolls are separate and that is the point: an eruption can bury **nothing**, which decouples "the
   * mountain erupted" from "a town died" - so [ERUPTION_RATE] can rise without repeating the mistake its own
   * KDoc records, because the thing that was too high was the *town-destruction* rate.
   */
  private fun resolveEruptions(year: Int) {
    if (vents.isEmpty()) return

    for (vent in vents) {
      val ventIndex = vent.index.toLong()
      if (roll(year.toLong(), ventIndex, ERUPTION_SALT) >= vent.strength * ERUPTION_RATE) continue

      val erupted = log(
        year, EventKind.ERUPTION, emptyList(), vent.position, emptyList(),
        "the mountain ${ventName(vent)} wakes and throws ash across ${nearReach(vent.position)}"
      )

      for (town in towns) {
        if (!town.standing) continue
        val distance = town.facts.position.distanceTo(vent.position)
        if (distance > ASH_REACH) continue

        // Nearer is worse, and the taper is what makes an eruption bury the village on its flank while the city
        // twenty kilometres downwind loses a harvest and survives to remember it.
        val chance = vent.strength * BURIAL_CHANCE * (1.0 - distance / ASH_REACH)
        if (roll(year.toLong(), ventIndex, town.facts.index.toLong(), ASHFALL_SALT) >= chance) continue

        abandon(
          town, year, EventKind.SETTLEMENT_BURIED, null, "buried in ash",
          ruinKind = SiteKind.ASH_RUIN, causes = listOf(erupted)
        )
      }
    }
  }

  /**
   * What the chronicle calls a volcano.
   *
   * A name of its own rather than "the mountain above <nearest town>", which is what this was first and read
   * badly for a measurable reason: most vents are nowhere near a settlement, so on a 256-cell world at seed 42
   * fifty-odd of sixty-one eruption lines came out as "the mountain above the wilds" - the same sentence over and
   * over, which is a chronicle a player would stop reading.
   *
   * Named through `Names.place`, so a volcano is named the way a town is and in the same culture's vocabulary.
   * That is worth more than tidier prose: a *named* mountain is something an NPC can refer to and a quest can
   * point at, which "the mountain above the wilds" never could.
   *
   * The caller prefixes "the mountain", and that is not redundancy. `Names.place` draws from a settlement
   * vocabulary - it produces Wolkscar and Dreargrube, which read as peaks, but also Greenleigh and Elmcombe,
   * which read as villages - so without the prefix half the eruption lines in a chronicle would look like a town
   * erupting. Giving `Names` a landform vocabulary would be the better fix and is a larger change than this
   * earns; it would also move `Names.catalogueDigest` and therefore every name in every world.
   *
   * The culture is the nearest town's, or the first there is. A landform has no culture of its own, and the
   * people who named it are whoever lives nearest to it.
   */
  private fun ventName(vent: VentFacts): String {
    val nearest = towns.minByOrNull { it.facts.position.distanceTo(vent.position) }
    val culture = nearest?.facts?.cultureIndex ?: 0
    return Names.place(Names.seedOf(worldSeed, VENT_NAME_SALT, vent.index.toLong()), culture)
  }

  /**
   * What an eruption's ash falls across: the nearest settled country, or the wilds.
   *
   * The second half of the sentence, and it carries the information the first half cannot - a player reading
   * "Grauwald wakes and throws ash across the country about Silberstein" knows which of their towns to worry
   * about, which is the whole point of an eruption being in a *town's* remembered history.
   */
  private fun nearReach(at: Vec2d): String {
    val nearest = towns.minByOrNull { it.facts.position.distanceTo(at) } ?: return "empty country"
    return if (nearest.facts.position.distanceTo(at) > ASH_REACH) {
      "empty country"
    } else {
      "the country about ${nameOf(nearest)}"
    }
  }

  /**
   * @param ruinKind what is left behind. Defaulted so every existing caller is untouched; an eruption is the one
   *   cause that leaves a mound rather than a scatter of walls, and the materialiser reads the kind rather than
   *   the chronicle - see [SiteKind.ASH_RUIN].
   * @param causes events this abandonment is a consequence of. Defaulted empty for the same reason, and what
   *   makes `prune`'s transitive closure able to thread an ash ruin back to the eruption that made it.
   */
  private fun abandon(
    town: Town,
    year: Int,
    cause: EventKind,
    byCiv: Int?,
    how: String,
    ruinKind: SiteKind = SiteKind.RUIN,
    causes: List<Int> = emptyList()
  ) {
    if (!town.standing) return

    town.abandoned = year
    town.ruinCause = cause
    val owner = town.owner
    val survivors = town.population
    if (owner >= 0) civs[owner].towns.remove(town.facts.index)
    town.owner = -1
    if (owner >= 0) relocateCapital(civs[owner])

    // The one ending the Orders argue about. Counted here rather than at the call site because `abandon` is the
    // single path every ending goes through, so there is one place for this to be right - and counted before
    // the owner is cleared would have been wrong, hence reading `owner` rather than `town.owner`.
    if (cause == EventKind.SETTLEMENT_FORSAKEN) {
      blightLosses++
      if (owner >= 0) civs[owner].townsLostSinceOath++
    }

    val actors = ArrayList<Actor>()
    actors.add(Actor(ActorType.SETTLEMENT, town.facts.index))
    byCiv?.let { actors.add(Actor(ActorType.CIV, it)) }

    val ending = log(
      year, cause, actors, town.facts.position, causes,
      "${nameOf(town)} is $how and stands empty"
    )

    // The ruin field is bigger than the town was, because a burned town spreads: the earthworks, the field
    // walls and the middens outlast the buildings. Capped, because the materialiser finds a point marker by
    // expanding a chunk's bounds by a fixed margin, and a ruin reaching past that margin would simply stop
    // at a straight line - see ChunkMaterializer.MARKER_MARGIN.
    val ruin = addSite(
      ruinKind, town.facts.position, year, town.facts.index,
      civ = town.foundingCiv,
      radius = min(town.facts.tier.footprintRadius * RUIN_SPREAD, MAX_RUIN_RADIUS),
      artifact = -1, figure = -1
    )
    town.sites.add(ruin)
    town.population = 0.0
    loseKeptRelics(town, ruin, year, ending)

    if (cause in REFUGEE_CAUSES) foundRefuge(town, survivors, owner, year, ending)
  }

  // --- The mana ---------------------------------------------------------------------------------------

  /**
   * The star, the blight, the wards and the towns the blight emptied.
   *
   * ### Why this is one pass and in this order
   *
   * The four are one causal chain and the chain is the product: nothing can be blighted before the star falls,
   * nobody wards against a blight they have not had, and a town is only forsaken after the wards failed or were
   * never raised. Running them as one pass in that order means every event this emits can cite the one before
   * it, so `Chronicle.provenanceOf` and the causal closure in [prune] thread a ruin in corrupted land all the
   * way back to [EventKind.STAR_FELL] - which is what makes a blighted province something a player can be
   * *told about* rather than something they walk into.
   */
  private fun resolveMana(year: Int) {
    letTheStarFall(year)
    if (wounds.isEmpty()) return

    for (town in towns) {
      if (!town.standing) continue
      if (town.facts.mana < params.blightMana) continue

      val index = town.facts.index.toLong()

      // How far past the threshold, normalised against the headroom above it, so a town on the very edge of a
      // province is at a fraction of the risk of one whose fields are inside it. A flat rate above the
      // threshold would make the blight a property of a boolean rather than of a place.
      val severity = ((town.facts.mana - params.blightMana) / (1.0 - params.blightMana)).coerceIn(0.0, 1.0)
      val relief = if (town.warded) WARD_RELIEF else 1.0

      if (roll(year.toLong(), index, BLIGHT_SALT) < severity * BLIGHT_RATE * relief) {
        val toll = (town.population * (BLIGHT_TOLL + BLIGHT_TOLL_SPREAD * severity)).toInt()

        /*
         * The floor is what stops the blight being an extinction event, and finding out why took a measurement.
         *
         * `growPopulations` abandons any settlement that falls below twelve people and has stood fifty years -
         * "dwindled to nothing", logged as `SETTLEMENT_ABANDONED`. A town is founded at a fifth of its potential,
         * so a modest site starts near that floor, and a blight taking a sixth of it drops straight through. The
         * settlement then ends by the *dwindling* path, which never consults `blights`, never logs
         * `SETTLEMENT_FORSAKEN`, and is not covered by the last-town guard below.
         *
         * Measured: seeds 24 and 26 finished their thousand years with **one standing settlement out of
         * twenty-eight**, and the forsaken count stayed at nought, which is precisely why the census did not show
         * it. Raising `blightMana` from 0.72 to 0.84 recovered four towns across twelve worlds - it was never the
         * threshold, it was the floor.
         *
         * So the toll cannot take a town below [BLIGHT_FLOOR], and never raises it either. `PLAGUE_RATE`'s
         * `population / 9000` factor is the same protection arrived at from the other side: a plague cannot
         * empty a village because a village is not crowded. A blight can reach any village at all, so the guard
         * has to be on the toll instead. Being *given up* stays the modelled ending, on the counter, below.
         */
        val floor = min(town.population, BLIGHT_FLOOR)
        val before = town.population
        town.population = max(floor, town.population - toll)
        val dead = (before - town.population).toInt()
        town.wealth *= BLIGHT_WEALTH_LOSS

        /*
         * The blight takes the *land*, not only the people on it, and that turned out to be the difference
         * between a mechanic and a trap.
         *
         * Modelled as a population toll alone, the blight found an equilibrium against logistic growth and held
         * the town at almost exactly `expansionPressure` - and that gate is hard, so `crowding` stayed near zero
         * and the founding chance with it. Seeds 24 and 26 finished their thousand years having founded **one
         * settlement**, peak population 8 756 against 30 249 with the blight off. Nothing died and nothing was
         * ruined; the civilisation simply never expanded, for ever, and no census column showed it because every
         * column counts things that happened.
         *
         * Reducing the carrying capacity fixes it and is the better model besides: blighted fields support fewer
         * people, so occupancy *rises* after a blight and the town sheds settlers instead of stagnating. A blight
         * now pushes settlement outwards, which is what a blight should do. `decline` is the existing mechanism
         * for exactly this - `besiege` uses it with `SACK_DECLINE` - and `growPopulations` clamps population to
         * the new capacity by itself, so the people leaving needs no code.
         */
        town.decline *= BLIGHT_DECLINE

        // A warded town's losses do not count towards being forsaken, which is the whole value of a ward: the
        // fields still fail some years and the people stay. Without this clause a ward delays an abandonment
        // instead of preventing one, and every town in a blighted province empties eventually - which would
        // make the wards a subsystem with no observable effect.
        if (!town.warded) town.blights++

        log(
          year, EventKind.BLIGHT_SPREAD,
          listOf(Actor(ActorType.SETTLEMENT, town.facts.index), Actor(ActorType.SITE, wounds.first())),
          town.facts.position, listOf(starFell),
          "the blight takes the fields of ${nameOf(town)}; $dead are lost"
        )
      }

      val civ = town.owner.takeIf { it >= 0 }?.let { civs[it] }

      val wardAfter = wardAfterBlights(civ)

      if (!town.warded && wardAfter != null && town.blights >= wardAfter &&
        civ != null && civ.technology >= params.wardTechnology
      ) {
        town.warded = true
        town.wardYear = year
        log(
          year, EventKind.WARD_RAISED,
          listOf(Actor(ActorType.SETTLEMENT, town.facts.index), Actor(ActorType.CIV, civ.index)),
          town.facts.position, listOf(starFell),
          "${nameOf(town)} sets wards against the blight after ${town.blights} failed harvests"
        )
        continue
      }

      if (!town.warded && town.blights >= params.blightsBeforeForsaking) {
        // **A people do not walk away from their last town.** They move to the next valley; they do not cease to
        // exist. Without this clause the blight is not a regional misfortune but an extinction event, and the
        // measurement is unambiguous: on seed 38 the founding civ's only settlement was blighted out inside the
        // first two centuries, `retireCivs` ended the civ that owned it, and the world finished its thousand
        // years with **two settlements ever founded and nought standing** - against twenty-eight with the blight
        // switched off. The spawner invariant is what noticed, because a world with no towns has no gentle
        // country in it.
        //
        // The other town-killers do not need this guard and it is worth knowing why rather than adding it to
        // them: `ERUPTION` fires at 0.0004 per tick and a razing needs a war, so both are effectively impossible
        // before a civ has spread. The blight fires at 0.10, and the star falls in the first tenth of the span -
        // which is exactly when a civ still has one town.
        val holdsAlone = civ == null || civ.towns.count { towns[it].standing } <= 1
        if (!holdsAlone) {
          abandon(town, year, EventKind.SETTLEMENT_FORSAKEN, null, "given up to the blight")
        }
      }
    }
  }

  /**
   * How many failed harvests a town endures before its wards go up, or **null for a people who never ward**.
   *
   * The sharpest thing an Order changes about a world, and the only one that can cost a town. Each of the three
   * answers is that Order's conviction applied to the one decision the mana subsystem actually offers:
   *
   * - **Eternity** wards a harvest earlier than anybody else. Holding the line is the whole of what they are.
   * - **Chaos** never wards at all. They are not failing to protect the town - they do not believe the fields
   *   should be protected, and the consequence is more [EventKind.SETTLEMENT_FORSAKEN] on their ground. The
   *   `holdsAlone` guard below still stops this from ending a civilisation, so a Chaos people loses towns and
   *   does not vanish.
   * - **The Circle**, and every unaligned people, wards on the ordinary schedule.
   *
   * Null rather than a very large number, deliberately: `WARD_AFTER_BLIGHTS` was very nearly given an
   * `Int.MAX_VALUE` sentinel here, which is the mistake `buildSite` has a whole paragraph about - a sentinel
   * that has to survive arithmetic is a sentinel in the wrong place. Null cannot be compared against by
   * accident.
   *
   * The `blightsBeforeForsaking > WARD_AFTER_BLIGHTS` requirement in [HistoryParams] still holds for everybody
   * this returns a number for, which is what keeps a ward reachable at all.
   */
  private fun wardAfterBlights(civ: Civ?): Int? = when (civ?.sworn) {
    Faction.ETERNITY -> max(1, WARD_AFTER_BLIGHTS - 1)
    Faction.CHAOS -> null
    else -> WARD_AFTER_BLIGHTS
  }

  /**
   * Once per world: something comes down, and the mana is in the world from then on.
   *
   * One event per wound in the same year - the star broke up on the way down, or it did not - with the first as
   * the cause of the rest. That is what makes all three candidates `SpecialSites.wounds` found *used*: a
   * subsystem that computes three places and only ever visits one is complete-tested-and-never-reached, and
   * a world with three mana provinces and one explanation would be visibly missing two.
   *
   * Early, in the first tenth of the span, so that the whole simulated history happens in a world that already
   * has mana in it rather than acquiring it near the end.
   */
  private fun letTheStarFall(year: Int) {
    if (starFell >= 0) return
    if (candidates.wounds.isEmpty()) return

    val window = max(1, (presentYear - startYear) / STAR_WINDOW_SHARE)
    val fallYear = startYear + (roll(STAR_SALT) * window).toInt()
    if (year < fallYear) return

    for (candidate in candidates.wounds) {
      val site = addSite(
        SiteKind.WOUND, candidate.position, year,
        // No settlement and no civ: this is the one site nobody built and nobody owns. `siteName` renders it
        // "of the wilds", which is right.
        settlement = -1, civ = -1,
        radius = WOUND_RADIUS, artifact = -1, figure = -1
      )
      wounds.add(site)

      val event = log(
        year, EventKind.STAR_FELL, listOf(Actor(ActorType.SITE, site)),
        candidate.position,
        if (starFell >= 0) listOf(starFell) else emptyList(),
        if (starFell >= 0) {
          "a shard of it comes down at ${siteName(sites[site])}"
        } else {
          "a star falls and breaks the ground at ${siteName(sites[site])}"
        }
      )
      if (starFell < 0) starFell = event
    }
  }

  /**
   * A prophet or a scholar walks out to the wound and does not come back.
   *
   * Deliberately **not** through [buryFigure], which is what "vanished" means: there is no barrow beside their
   * home town, so they show up in `chronicle -Pquests` under "figures with no known grave" for free. If they
   * were carrying a relic it stays out there, which puts a named artifact in the middle of the most dangerous
   * ground in the world - the endgame hook the corrupted land exists to hold, and it comes out of the log
   * rather than being placed.
   */
  private fun vanishSeers(year: Int) {
    if (wounds.isEmpty()) return

    for (person in people.toList()) {
      if (person.death != 0) continue
      if (person.role != FigureRole.PROPHET && person.role != FigureRole.SCHOLAR) continue

      val home = towns[person.home].facts.position
      val wound = wounds.minByOrNull { sites[it].position.distanceTo(home) } ?: continue
      val at = sites[wound].position
      if (at.distanceTo(home) > params.seerRange) continue

      // A seer sworn to Chaos is likelier to go, and it is their *own* Order that decides rather than their
      // city's: `person.sworn` is fixed at birth, so a prophet who was born into a Chaos people and outlived
      // its schism still walks out to the wound. That is the better story and it is also the truer model -
      // this is one person choosing to go, which is the whole reason the event exists.
      val leaving = if (person.sworn == Faction.CHAOS) SEER_LOSS_CHANCE * CHAOS_SEER_ZEAL else SEER_LOSS_CHANCE
      if (roll(year.toLong(), person.index.toLong(), SEER_SALT) >= leaving) continue

      person.death = year
      // Where they were last going, not where they lived. `slainAt` already means exactly that for a general
      // who fell in a battle, so the field is reused rather than a second one added.
      person.slainAt = at

      val lost = log(
        year, EventKind.SEER_VANISHED,
        listOf(Actor(ActorType.FIGURE, person.index), Actor(ActorType.SITE, wound)),
        at, listOf(starFell),
        "${personName(person)} goes out to ${siteName(sites[wound])} and is not seen again"
      )

      val relic = relics.firstOrNull { it.holder == person.index && it.resting < 0 } ?: continue
      val grave = addSite(
        SiteKind.TOMB, offset(at, person.index.toLong(), WOUND_RELIC_OFFSET),
        year, settlement = -1, civ = person.civ,
        radius = TOMB_RADIUS, artifact = relic.index, figure = person.index
      )
      relic.holder = -1
      relic.resting = grave
      relic.provenance.add(
        log(
          year, EventKind.ARTIFACT_LOST,
          listOf(Actor(ActorType.ARTIFACT, relic.index), Actor(ActorType.SITE, grave)),
          sites[grave].position, listOf(lost),
          "${relicName(relic)} goes with ${personName(person)} into the blighted land"
        )
      )
    }
  }

  /**
   * A figure crosses the deep waste, dies in it, and is buried where they fell.
   *
   * ### What this exists to fix
   *
   * `Person.slainAt` has always recorded where somebody actually died, and [buryFigure] has always discarded it
   * and put the barrow 320 m from their **home town** instead. Since settlements are placed by habitability,
   * that meant no world ever had a tomb in a desert - the harsh biomes held wounds and nothing else, and the
   * one thing a player crossing them could find was a monster. This pass is the other half of `slainAt`.
   *
   * ### Why an explorer or a general rather than anybody
   *
   * The two roles with a reason to be out there - one goes looking and one marches. A [FigureRole.RULER] does
   * not cross a desert and a
   * [FigureRole.PROPHET] is already spoken for by [vanishSeers] - which is also why this runs after it. What
   * makes the event worth having is that the grave is *explained*: the chronicle says who they were, which
   * civilisation they served and that they were lost in the waste, so a barrow a player digs up has a name on it
   * and a line in the log that can be found from the other end.
   *
   * ### Unlike a seer, this leaves a grave
   *
   * [vanishSeers] deliberately leaves none - that is what "vanished" means, and a relic left loose in the
   * blighted land is the endgame hook. Here the point is the opposite: somebody found the body, or the sand did
   * the burying, and either way there is something to dig. It goes through [addSite] directly rather than
   * [buryFigure] for exactly the reason that function cannot serve: its position is the point.
   */
  private fun loseTravellers(year: Int) {
    if (candidates.wastes.isEmpty()) return

    for (person in people.toList()) {
      if (person.death != 0) continue
      if (person.role != FigureRole.EXPLORER && person.role != FigureRole.GENERAL) continue

      val home = towns[person.home].facts.position
      // The nearest waste this person could plausibly have been crossing. `candidates.wastes` is already
      // filtered to ground within `seerRange` of *some* settlement, so this only has to pick which one.
      val waste = candidates.wastes
        .filter { it.position.distanceTo(home) <= params.seerRange }
        .maxByOrNull { it.quality } ?: continue

      // Scaled by how lethal the ground is, so the deep desert takes more travellers than the badlands do.
      val chance = TRAVELLER_LOSS_CHANCE * waste.quality
      if (roll(year.toLong(), person.index.toLong(), TRAVELLER_SALT) >= chance) continue

      person.death = year
      person.slainAt = waste.position

      val lost = log(
        year, EventKind.TRAVELLER_LOST,
        listOf(Actor(ActorType.FIGURE, person.index), Actor(ActorType.CIV, person.civ)),
        waste.position, emptyList(),
        "${personName(person)} is lost crossing the waste and is buried where they fell"
      )

      val relic = relics.firstOrNull { it.holder == person.index && it.resting < 0 }
      // Offset off the exact candidate position for `buryFigure`'s reason: a candidate sits at a scan-lattice
      // cell centre, and a barrow on a 4 km lattice reads as placed by a grid rather than by a death.
      val grave = addSite(
        SiteKind.TOMB, offset(waste.position, person.index.toLong(), TOMB_OFFSET),
        year, settlement = -1, civ = person.civ,
        radius = TOMB_RADIUS, artifact = relic?.index ?: -1, figure = person.index
      )
      person.resting = grave

      if (relic != null) {
        relic.holder = -1
        relic.resting = grave
        relic.provenance.add(
          log(
            year, EventKind.ARTIFACT_ENTOMBED,
            listOf(Actor(ActorType.ARTIFACT, relic.index), Actor(ActorType.FIGURE, person.index),
              Actor(ActorType.SITE, grave)),
            sites[grave].position, listOf(lost),
            "${relicName(relic)} is buried with ${personName(person)} out in the waste"
          )
        )
      }
    }
  }

  // --- The Orders -------------------------------------------------------------------------------------

  /**
   * Who is sworn to which Order, who changed their mind, and what they did about it.
   *
   * ### The whole pass is absent from most worlds
   *
   * It returns before drawing a single roll when [OrderInfluence.isAbsent], which is the default and what a
   * world with no previous incarnation gets. Because every decision in this simulation is a *keyed* roll rather
   * than a draw from a stream, returning early consumes nothing - so a world generated with the Orders off is
   * identical to one generated before they existed, event for event and id for id. `OrderHistoryTest` asserts
   * exactly that, and it is the property that let this ship without regenerating Genesis.
   *
   * ### Why the Orders are here at all, this early
   *
   * They are not a late addition to a world's history: they are as old as the argument they are having, which
   * is as old as the mana. A people can swear before the star falls - the conviction that the world runs in
   * cycles does not need this cycle's evidence - and the leaning terms in [leaningOf] then pull them towards
   * whichever Order their own century bore out.
   *
   * ### Three gates, and the first is the important one
   *
   * [HistoryParams.orderSwornShare] decides, once per civilisation and from its index alone, whether it is the
   * sort of people who swear at all. Two in five never do. That is deliberately the strongest lever in the
   * subsystem: it is what keeps the Orders a thread running through a history rather than the subject of one,
   * and it is why a chronicle reads as a world with religion in it rather than a world about religion.
   */
  private fun swearOrders(year: Int) {
    if (params.orderInfluence.isAbsent) return

    for (civ in civs.toList()) {
      if (!civ.exists) continue

      if (civ.sworn == null) swear(civ, year) else reconsider(civ, year)
      performRite(civ, year)
    }
  }

  /**
   * Whether this people ever swears at all: decided once, from its index.
   *
   * A pure function of the civ index rather than a stored flag or a per-tick roll, and the per-tick version is
   * the one to avoid. Under a per-tick chance a civilisation that survived a thousand years would end up sworn
   * almost certainly and one that fell in two centuries almost never, so [HistoryParams.orderSwornShare] would
   * quietly become a statement about how the wars went instead of the share it claims to be.
   */
  private fun maySwear(civ: Civ): Boolean =
    roll(civ.index.toLong(), ORDER_SHARE_SALT) < params.orderSwornShare

  private fun swear(civ: Civ, year: Int) {
    if (!maySwear(civ)) return
    if (roll(year.toLong(), civ.index.toLong(), ORDER_SWEAR_SALT) >= params.orderSwearChance) return

    val seat = seatOf(civ) ?: return
    val order = drawOrder(civ, year, ORDER_DRAW_SALT, withLeaning = true) ?: return
    val capital = towns[seat]

    civ.sworn = order
    civ.swornYear = year
    civ.townsLostSinceOath = 0
    civ.blightLossesAtOath = blightLosses

    // The founding, and the star too once it has fallen. Both, because they are different explanations of the
    // same oath - who these people are, and what they are reacting to - and `prune`'s causal closure keeps
    // whichever of them survives importance pruning.
    val causes = ArrayList<Int>(2)
    if (civ.foundedEvent >= 0) causes.add(civ.foundedEvent)
    if (starFell >= 0) causes.add(starFell)

    civ.swornEvent = log(
      year, EventKind.ORDER_SWORN,
      listOf(Actor(ActorType.CIV, civ.index), Actor(ActorType.SETTLEMENT, seat)),
      capital.facts.position, causes,
      "the ${civName(civ)} swear themselves to ${order.label} at ${nameOf(capital)}"
    )
  }

  /**
   * The town a civilisation's oaths are sworn at: its largest **standing** settlement, or null if it holds none.
   *
   * Standing, not [Civ.capital], and the difference is a bug the seed sweep caught. `capital` is fixed when the
   * civ is seeded and is never revised, so a people whose first city was razed in year 300 still names it as
   * their capital in year 900 - and an oath logged there is an event outside that settlement's lifetime, which is
   * exactly what `Invariants.checkEventsRespectSettlementLifetimes` exists to forbid. It found **eleven** across
   * 120 seeds; none of the unit tests could, because they run four seeds on which every capital happened to
   * survive.
   *
   * Null when a civ holds nothing standing, and the callers return on it. A people with no towns left should not
   * be taking oaths, which is the right answer rather than a guard: [retireCivs] is about to end them anyway.
   *
   * `updateFigures` picks a figure's home town by the same rule, which is where the idiom comes from.
   */
  private fun seatOf(civ: Civ): Int? = civ.towns
    .filter { towns[it].standing }
    .maxByOrNull { towns[it].population }

  /**
   * A sworn people whose own century argues against their Order may abandon it.
   *
   * Gated on **contradiction** as well as on a roll, so a people whose convictions were borne out never
   * reconsiders at all. That is what makes a schism worth reading: it is evidence, in the log, that this
   * particular city tried something for two centuries and watched it fail.
   */
  private fun reconsider(civ: Civ, year: Int) {
    val held = civ.sworn ?: return

    /*
     * A people changes its mind at most once, and the cap is load-bearing rather than tidiness.
     *
     * Without it the first world generated came out with the Wheatshire swearing to Eternity in year 26,
     * forsaking it for the Circle in 486, and forsaking *that* for Chaos in 791 - a cascade, and on inspection
     * a structural one. Blight contradicts both Eternity and the Circle and is the *only* thing that
     * contradicts them, while Chaos is contradicted by centuries in which nothing happens. So on any world
     * with a live blight there is a one-way drift into Chaos, and every civilisation ends up there eventually
     * no matter what `OrderInfluence` said - which would quietly make the whole tunable ornamental.
     *
     * Capping revisions fixes it at the root instead of by retuning `schismChance` downwards, which would only
     * have made the drift slower. The first oath is where the world's weights are read, so bounding the
     * revisions is what keeps the previous incarnation's victor visible in the outcome. One revision also
     * happens to be the most a people can make and still be believed.
     */
    if (civ.schisms >= MAX_SCHISMS) return

    // An oath is not reconsidered in the century it was taken. Without this a civ can swear and recant inside
    // two ticks, which reads as indecision rather than as a schism.
    if (year - civ.swornYear < ORDER_OATH_GRACE) return
    if (!contradicted(civ, held, year)) return

    /*
     * A tradition this world holds strongly is one its people abandon less readily.
     *
     * Without this the favoured Order can only ever *lose* members to a schism and never gain them, because a
     * schism excludes the Order being abandoned - so its weight, however large, has no say in where a schism
     * lands. Combined with the Circle having the lowest contradiction bar by design, weighting the Circle twenty
     * to one produced a world whose first oaths were nearly all Circle and whose present day held almost none:
     * it won every draw and then bled out. `OrderHistoryTest` caught it.
     *
     * Scaling by the Order's share of the world's total influence fixes it at the right level, because that is
     * what influence over a *history* should mean - not only how many peoples take the oath, but how well the
     * institution holds them. A share of exactly a third leaves the chance untouched, so a balanced world
     * behaves as it did.
     */
    val share = params.orderInfluence.weightOf(held) / params.orderInfluence.total
    val stickiness = max(1.0, share * Faction.entries.size)
    if (roll(year.toLong(), civ.index.toLong(), SCHISM_SALT) >= params.schismChance / stickiness) return

    // Excluded rather than redrawn-and-checked: the old form aborted the whole schism when the draw came back
    // with the Order being abandoned, which wasted a contradiction that had taken centuries to earn.
    val next = drawOrder(civ, year, SCHISM_DRAW_SALT, withLeaning = false, exclude = held) ?: return

    // Their largest standing town, not `capital` - see `seatOf` for the eleven sweep violations that came of
    // naming a settlement that had been a ruin for centuries.
    val seat = seatOf(civ) ?: return
    val capital = towns[seat]
    civ.sworn = next
    civ.swornYear = year
    civ.townsLostSinceOath = 0
    civ.blightLossesAtOath = blightLosses
    civ.schisms++

    val causes = if (civ.swornEvent >= 0) listOf(civ.swornEvent) else emptyList()
    civ.swornEvent = log(
      year, EventKind.ORDER_SCHISM,
      listOf(Actor(ActorType.CIV, civ.index), Actor(ActorType.SETTLEMENT, seat)),
      capital.facts.position, causes,
      "the ${civName(civ)} forsake ${held.label} for ${next.label}"
    )
  }

  /**
   * Whether what this people lived through argues against the Order they hold.
   *
   * One clause per Order, and each is that Order's promise failing **on that Order's own terms** - which took a
   * correction worth recording, because the obvious signal was the wrong one.
   *
   * All three clauses were first written against `blightsSinceOath`, a count of failed harvests. Measured on a
   * 512 km world that produced **no surviving Eternity civilisation at all**: every people that swore to it
   * schismed away, because a failed harvest is common and so Eternity was the only Order in permanent
   * contradiction. And it was wrong on the lore as well as on the numbers - Eternity does not promise to
   * abolish the mana, which its own doctrine calls impossible; it promises to *contain* it. A warded town whose
   * fields fail some years and whose people stay is Eternity's promise being **kept**.
   *
   * So the signal is a town actually *lost*:
   *
   * - **Eternity** promised the line could be held **at any cost**, and is therefore the hardest of the three to
   *   shake: it takes [ETERNITY_SCHISM_LOSSES] towns given up before its own people stop believing it.
   * - **Chaos** promised an ending. Centuries in which nothing anywhere ended say it is not coming - and that
   *   is a fact about the whole world, not about this civilisation, because Chaos is not waiting for its own
   *   towns to fall.
   * - **The Circle** is the swing vote by design, and one loss is enough to tell it the world is off its hour.
   *
   * ### The bars are ordered, and the order was wrong first
   *
   * Eternity's bar started at one loss and the Circle's at two, which drained Eternity: it was the easiest of
   * the three to contradict, so nearly every schism in every world *originated* there and it held 3 of 21 sworn
   * peoples under equal weights. Inverting the two fixed the distribution and is the better reading of the
   * design besides - `factions.md` has Eternity holding "at any cost" and calls the Circle "the swing vote"
   * whose "allegiance flips", so the Order that changes its mind most readily should be the Circle.
   */
  private fun contradicted(civ: Civ, held: Faction, year: Int): Boolean = when (held) {
    Faction.ETERNITY -> civ.townsLostSinceOath >= ETERNITY_SCHISM_LOSSES
    Faction.CHAOS -> blightLosses == civ.blightLossesAtOath && year - civ.swornYear >= ORDER_PATIENCE
    Faction.CIRCLE -> civ.townsLostSinceOath >= CIRCLE_SCHISM_LOSSES
  }

  /**
   * Draws an Order for a civilisation: the world's weights, times what this people has lived through.
   *
   * The multiplication is the design. [OrderInfluence] is a property of the *world* - the residue of which
   * Order won the last one - and [leaningOf] is a property of this civilisation's own thousand years. Neither
   * alone would do: weights alone make three interchangeable peoples wearing different colours, and leaning
   * alone makes the previous world's victor irrelevant, which is the feature.
   *
   * Returns null only when every weight is zero, which [OrderInfluence.isAbsent] has already excluded - so in
   * practice never, and the null is there so a designer who zeroes all three from a params file gets no Orders
   * rather than an exception.
   */
  private fun drawOrder(
    civ: Civ,
    year: Int,
    salt: Long,
    /**
     * Whether what this people lived through weighs on the draw. True for a first oath, **false for a schism**.
     *
     * That asymmetry is the fix for the last and largest bias in this subsystem, and it took printing the oaths
     * to find. With everything else balanced, first oaths came out even - Chaos 3, Eternity 3, Circle 1 across
     * three seeds - and **every schism in every seed went to Chaos**. Structurally so: a schism only fires when
     * the held Order was contradicted, contradiction means towns lost, and a lost town both raises Chaos'
     * "damage nobody answered" term and removes a warded town from Eternity's. The evidence that discredits an
     * Order is the same evidence that recommends its opposite.
     *
     * So a schism reads the world's weights alone. A people abandoning a conviction is not being pushed by the
     * ground - it is choosing among the traditions its world actually has, which is what `OrderInfluence`
     * describes. It also makes schisms *amplify the previous incarnation's victor* rather than a hidden tilt,
     * which is the behaviour the feature was asked for.
     */
    withLeaning: Boolean,
    /** An Order this draw may not return. A schism excludes the one being abandoned. */
    exclude: Faction? = null
  ): Faction? {
    val weights = DoubleArray(Faction.entries.size) { i ->
      val faction = Faction.entries[i]
      if (faction == exclude) {
        0.0
      } else {
        params.orderInfluence.weightOf(faction) * if (withLeaning) leaningOf(civ, faction) else 1.0
      }
    }

    val total = weights.sum()
    if (total <= 0.0) return null

    var pick = roll(year.toLong(), civ.index.toLong(), salt) * total
    for (i in weights.indices) {
      pick -= weights[i]
      if (pick < 0.0) return Faction.entries[i]
    }
    // Only reachable through floating-point drift at the very top of the range.
    return Faction.entries.last()
  }

  /**
   * How much this civilisation's own history pulls it towards one Order, as a multiplier from 1 up.
   *
   * Deliberately never *below* one, so a leaning can only ever add. A term that dropped below one would make
   * two of the three Orders less likely at once, which is a different and much blunter statement than making
   * one of them more likely - and it would let circumstance overwhelm the world's weights entirely on a civ
   * that happened to tick no boxes.
   *
   * [LEANING_STRENGTH] is 1.0, so a fully-borne-out conviction doubles that Order's weight. Worth comparing
   * against the previous world's victor bonus, which defaults to 0.5: **what a people lived through matters
   * more than which Order won the last world**, which is the ordering the whole design wants. The victor tilts
   * a world; it does not decide any single civilisation.
   */
  private fun leaningOf(civ: Civ, faction: Faction): Double {
    val held = civ.towns.map { towns[it] }
    if (held.isEmpty()) return 1.0

    /*
     * The shared premise: how much of this people's ground the mana has already claimed.
     *
     * **Chaos and Eternity read the same number, and getting there took two measurements.**
     *
     * The first version had Eternity lean on damage suffered and Chaos on proximity to a wound within
     * `seerRange`. That range is 70 km - generous on purpose, because it describes one person choosing to walk -
     * and as a statement about a *civilisation* it covers most of a 384 km world, so nearly every civ scored the
     * full Chaos bonus. Chaos took 8 of 12 sworn peoples across four seeds against a weight entitling it to 5.
     *
     * Narrowing that to each town's own mana exposure barely helped: with **equal** weights Chaos still took 11
     * of 18. The bias was structural, not a matter of range. Chaos' term was available from year one while
     * Eternity's was zero until something had burned, and `orderSwearChance` puts most oaths in the first
     * century - so Eternity could hardly ever win an early draw, whatever the weights said.
     *
     * The fix is to stop asking terrain to decide *which* conviction. Mana exposure is the premise both Orders
     * argue from - a people with no mana near them has little reason to hold either view strongly - so it raises
     * both equally, and what separates them is the world's `OrderInfluence`. Which is the whole point of the
     * feature: the previous incarnation's victor should be what tips a world, and it cannot be if the ground has
     * already decided.
     */
    val exposure = held.count { it.facts.mana >= params.blightMana }.toDouble() / held.size

    val share = when (faction) {
      /*
       * The premise, plus damage that **was** answered: wards raised, and the town still standing behind them.
       *
       * Reactive, so it accrues over a history rather than existing at year one - and it is the mirror of Chaos'
       * term below rather than a different kind of signal, which is what keeps the two symmetric early on.
       */
      Faction.ETERNITY -> exposure * PREMISE_SHARE +
          held.count { it.warded }.toDouble() / held.size * (1.0 - PREMISE_SHARE)

      /*
       * The premise, plus damage that was **not** answered: fields lost with no wards ever raised over them.
       *
       * The same evidence Eternity reads, read the opposite way, which is exactly what the two Orders do with
       * it. Eternity sees a line that was held; Chaos sees one that could not be.
       */
      Faction.CHAOS -> exposure * PREMISE_SHARE +
          held.count { it.blights > 0 && !it.warded }.toDouble() / held.size * (1.0 - PREMISE_SHARE)

      // Instruments, records and the arithmetic to use them. A people who can measure the cycle are the ones
      // who come to believe it has a schedule - and the one term here that is about neither mana nor damage,
      // because the Circle's position is not a reaction to either.
      Faction.CIRCLE -> civ.technology
    }

    return 1.0 + LEANING_STRENGTH * share.coerceIn(0.0, 1.0)
  }

  /**
   * An Order's signature working, performed somewhere it means something.
   *
   * The quietest of the four Order events and the one doing the "a hint here and there" work: it leaves nothing
   * on the ground, it recurs across a thousand years, and because [SettlementLoreService] surfaces a town's own
   * events it is what a townsperson brings up without being asked.
   *
   * Each Order's rite needs a *place*, and not having one is a legitimate reason for nothing to happen: Chaos
   * needs a wound in reach, Eternity needs a town of theirs that has actually been blighted, the Circle needs
   * one of its own shrines to read from. A world where an Order never finds its place simply has no rites from
   * it, which is a truer answer than a generic ceremony.
   */
  private fun performRite(civ: Civ, year: Int) {
    val order = civ.sworn ?: return
    if (roll(year.toLong(), civ.index.toLong(), RITE_SALT) >= params.riteChance) return

    val standing = civ.towns.filter { towns[it].standing }
    if (standing.isEmpty()) return

    when (order) {
      Faction.CHAOS -> {
        val wound = wounds.filter { site ->
          standing.any { sites[site].position.distanceTo(towns[it].facts.position) <= params.seerRange }
        }.pick(year, civ, RITE_PLACE_SALT) ?: return
        log(
          year, EventKind.RITE_PERFORMED,
          listOf(Actor(ActorType.CIV, civ.index), Actor(ActorType.SITE, wound)),
          sites[wound].position, listOfNotNull(civ.swornEvent.takeIf { it >= 0 }, starFell.takeIf { it >= 0 }),
          "the ${civName(civ)} open the ground wider at ${siteName(sites[wound])}"
        )
      }

      Faction.ETERNITY -> {
        val town = standing.filter { towns[it].blights > 0 }.pick(year, civ, RITE_PLACE_SALT) ?: return
        log(
          year, EventKind.RITE_PERFORMED,
          listOf(Actor(ActorType.CIV, civ.index), Actor(ActorType.SETTLEMENT, town)),
          towns[town].facts.position, listOfNotNull(civ.swornEvent.takeIf { it >= 0 }),
          "the ${civName(civ)} sing the long watch over the fields of ${nameOf(towns[town])}"
        )
      }

      Faction.CIRCLE -> {
        val shrine = sites.filter { it.kind == SiteKind.SHRINE && it.civ == civ.index }
          .pick(year, civ, RITE_PLACE_SALT) ?: return
        log(
          year, EventKind.RITE_PERFORMED,
          listOf(Actor(ActorType.CIV, civ.index), Actor(ActorType.SITE, shrine.index)),
          shrine.position, listOfNotNull(civ.swornEvent.takeIf { it >= 0 }),
          "the ${civName(civ)} take the reckoning at ${siteName(shrine)} and set the hour"
        )
      }
    }
  }

  /**
   * A sworn people raises a shrine: a cairn at a wound, a ward stone on a frontier, a circle on high ground.
   *
   * [buildSites]' shape, and not a call into [buildSite], for one reason that is worth being explicit about:
   * that helper writes `resource = candidate.detail` and takes no Order, so a shrine routed through it would
   * reach [addSite] with the discriminator its own materialiser needs left null. Sharing the gate would mean
   * widening a signature every one of the four other kinds would then carry an unused argument for.
   *
   * What it does share is every *rule*: the technology gate, the interval so shrines arrive across a history
   * rather than in one decade, `siteSeparation` so two Orders never build on one hilltop, and the requirement
   * that a standing town of this civ be near enough to have raised and tended it.
   */
  private fun raiseShrines(year: Int) {
    if (params.orderInfluence.isAbsent) return

    for (civ in civs.toList()) {
      if (!civ.exists) continue
      val order = civ.sworn ?: continue
      if (civ.technology < params.shrineTechnology) continue

      val last = civ.lastBuilt[SiteKind.SHRINE]
      if (last != null && year - last < params.builtSiteInterval) continue
      if (roll(year.toLong(), civ.index.toLong(), SHRINE_SALT) >= params.builtSiteChance) continue

      val from = candidates.shrines[order] ?: continue

      for (candidate in from) {
        // One shrine per place, whoever raised it - and across Orders, not within one, which is the point.
        // Two rival Orders working the same hilltop is a war rather than two shrines, and on the map it would
        // be one marker on top of another.
        if (sites.any {
            it.kind == SiteKind.SHRINE && it.position.distanceTo(candidate.position) < params.siteSeparation
          }
        ) {
          continue
        }

        val host = nearestStandingTown(civ, candidate.position, params.shrineRange) ?: continue

        /*
         * One shrine per town, which is a placement rule and also the fix for a naming collision.
         *
         * `siteSeparation` keeps two shrines 12 km apart, and two shrines 13 km apart can still have the same
         * nearest town - so a Circle people with four stone circles had two of them hosted by Wenton, and
         * `Names.site`'s `else` branch renders "the $form of $of" **ignoring the seed**. Both came out as "the
         * circle of Wenton", and the chronicle then referred to two different places by one name for a thousand
         * years. That is the defect `Names.site`'s `wound` branch already documents solving once.
         *
         * Fixed here rather than in `Names` deliberately. Giving shrines an epithet pool would work and would
         * move `Names.catalogueDigest()`, which renames every place in every world - a large cosmetic diff to
         * buy something a placement rule gives for free. And the rule is better modelling on its own terms: an
         * Order's shrines spread across the territory it holds instead of clustering on one town.
         */
        if (sites.any { it.kind == SiteKind.SHRINE && it.settlement == host }) continue

        val site = addSite(
          SiteKind.SHRINE, candidate.position, year, host, civ.index, SHRINE_RADIUS,
          artifact = -1, figure = -1, faction = order
        )
        towns[host].sites.add(site)
        civ.lastBuilt[SiteKind.SHRINE] = year

        log(
          year, EventKind.SHRINE_RAISED,
          listOf(Actor(ActorType.SITE, site), Actor(ActorType.CIV, civ.index)),
          candidate.position, listOfNotNull(civ.swornEvent.takeIf { it >= 0 }),
          "the ${civName(civ)} raise ${siteName(sites[site])} for ${order.label} above " +
              nameOf(towns[host])
        )
        // One per civ per pass, like every other built site. The next civ still gets its turn this tick.
        break
      }
    }
  }

  // --- Figures and artifacts -------------------------------------------------------------------------

  private fun updateFigures(year: Int) {
    for (civ in civs.toList()) {
      if (!civ.exists) continue

      val chance = params.yearsPerTick.toDouble() / params.yearsPerFigure
      if (roll(year.toLong(), civ.index.toLong(), FIGURE_SALT) >= chance) continue

      val home = civ.towns.filter { towns[it].standing }
        .maxByOrNull { towns[it].population } ?: continue
      // One ruler at a time: while one lives, the next comes by succession, not by rising.
      val role = roleFor(civ, year, rulerWanted = civ.ruler < 0)
      val person = newPerson(role, civ, home, year)
      val parent = parentFor(civ, person, year)
      if (parent != null) {
        person.parent = parent.index
        person.dynasty = parent.dynasty
      }
      if (role == FigureRole.RULER) {
        civ.ruler = person.index
        if (person.dynasty < 0) foundHouse(person, civ, year)
      }

      val family = parent?.let { ", child of ${personName(it)}" } ?: ""
      val house = person.dynasty.takeIf { it >= 0 }?.let { " of ${houseName(dynasties[it])}" } ?: ""
      log(
        year, EventKind.FIGURE_ROSE,
        listOf(Actor(ActorType.FIGURE, person.index), Actor(ActorType.CIV, civ.index)),
        towns[home].facts.position, emptyList(),
        "${personName(person)}$house$family rises as ${article(role)} of the ${civName(civ)} at ${nameOf(towns[home])}"
      )
    }

    for (person in people.toList()) {
      if (person.death != 0) continue
      if (year - person.birth < params.figureLifespan) continue
      person.death = year
      buryFigure(person, year, EventKind.FIGURE_DIED, "dies of age")
    }
  }

  private fun newPerson(role: FigureRole, civ: Civ, home: Int, year: Int): Person {
    val person = Person(
      index = people.size,
      nameSeed = Names.seedOf(worldSeed, FIGURE_NAME_SALT, people.size.toLong()),
      role = role,
      civ = civ.index,
      home = home,
      birth = year
    )
    // Their people's Order, which is null on a world where the Orders play no part. Not drawn separately:
    // a person who dissents from the city they lead is a good story, and the mechanism for it is a schism
    // logged against the civ - inventing a second, unlogged disagreement here would put a conviction on a
    // figure that no event in the chronicle can account for.
    person.sworn = civ.sworn
    people.add(person)
    return person
  }

  /**
   * Now and then a figure is the child of an earlier one of the same people: someone who rose at least a
   * generation ago and is alive or not long dead. Same home town first, so a craft or a command runs in a family.
   */
  private fun parentFor(civ: Civ, child: Person, year: Int): Person? {
    if (roll(year.toLong(), child.index.toLong(), CHILD_SALT) >= params.childChance) return null

    val candidates = people.filter {
      it.civ == civ.index && it.index != child.index && it.birth <= year - params.generationYears &&
          (it.death == 0 || year - it.death <= params.generationYears)
    }.sortedBy { if (it.home == child.home) 0 else 1 }
    if (candidates.isEmpty()) return null

    val sameHome = candidates.filter { it.home == child.home }.ifEmpty { candidates }
    return sameHome[(roll(year.toLong(), child.index.toLong(), PARENT_PICK_SALT) * sameHome.size).toInt()]
  }

  private fun foundHouse(founder: Person, civ: Civ, year: Int) {
    val dynasty = Dynasty(
      index = dynasties.size,
      nameSeed = Names.seedOf(worldSeed, HOUSE_NAME_SALT, dynasties.size.toLong()),
      founder = founder.index,
      civ = civ.index,
      seat = founder.home,
      founded = year
    )
    dynasties.add(dynasty)
    founder.dynasty = dynasty.index
    civ.house = dynasty.index
  }

  /**
   * A ruler has died: an heir of the same house takes the throne at once, or the house has no heir and the
   * people quarrel over who rules. The quarrel ends with a new house on the throne and may split the civ or send
   * the old house into exile.
   */
  private fun succeed(civ: Civ, dead: Person, year: Int, death: Int) {
    civ.ruler = -1
    if (!civ.exists) return
    val seat = seatOf(civ) ?: return

    if (roll(year.toLong(), civ.index.toLong(), HEIR_SALT) < params.heirChance) {
      val heir = newPerson(FigureRole.RULER, civ, seat, year)
      heir.parent = dead.index
      heir.dynasty = dead.dynasty
      civ.ruler = heir.index
      log(
        year, EventKind.SUCCESSION,
        listOf(
          Actor(ActorType.FIGURE, heir.index), Actor(ActorType.FIGURE, dead.index), Actor(ActorType.CIV, civ.index)
        ),
        towns[seat].facts.position, listOf(death),
        "${personName(heir)} succeeds ${personName(dead)}, their parent, as ruler of the ${civName(civ)}"
      )
      return
    }

    val crisis = log(
      year, EventKind.SUCCESSION_CRISIS,
      listOf(Actor(ActorType.FIGURE, dead.index), Actor(ActorType.CIV, civ.index)),
      towns[seat].facts.position, listOf(death),
      "${personName(dead)} dies without an heir, and the ${civName(civ)} quarrel over the throne"
    )
    val oldHouse = dead.dynasty.takeIf { it >= 0 }?.let { dynasties[it] }
    oldHouse?.ended = year

    val usurper = newPerson(FigureRole.RULER, civ, seat, year)
    civ.ruler = usurper.index
    foundHouse(usurper, civ, year)
    log(
      year, EventKind.SUCCESSION,
      listOf(
        Actor(ActorType.FIGURE, usurper.index), Actor(ActorType.FIGURE, dead.index), Actor(ActorType.CIV, civ.index)
      ),
      towns[seat].facts.position, listOf(crisis),
      "${personName(usurper)} takes the throne of the ${civName(civ)} and founds ${houseName(dynasties[civ.house])}"
    )

    val outcome = roll(year.toLong(), civ.index.toLong(), CRISIS_SALT)
    when {
      oldHouse != null && outcome < params.crisisExileChance -> exileHouse(oldHouse, civ, year, crisis)
      outcome < params.crisisExileChance + params.crisisSplitChance -> splitOver(civ, year, crisis)
    }
  }

  /** The house that lost leaves with its followers and founds a people at the remotest empty site. */
  private fun exileHouse(house: Dynasty, from: Civ, year: Int, crisis: Int) {
    val site = remotestEmptySite { true } ?: return
    val town = towns[site]
    val civ = addCiv(from.cultureIndex, town, year, CivOrigin.EXILED, from.index)
    joinCiv(civ, town, year)
    civ.technology = from.technology

    val leader = newPerson(FigureRole.RULER, civ, site, year)
    leader.dynasty = house.index
    civ.ruler = leader.index
    civ.house = house.index
    house.ended = 0
    house.exiledTo = civ.index
    hostility.set(from.index, civ.index, params.secessionHostility)

    civ.foundedEvent = log(
      year, EventKind.DYNASTY_EXILED,
      listOf(Actor(ActorType.CIV, civ.index), Actor(ActorType.CIV, from.index), Actor(ActorType.FIGURE, leader.index),
        Actor(ActorType.SETTLEMENT, site)),
      town.facts.position, listOf(crisis),
      "${personName(leader)} leads ${houseName(house)} out of the ${civName(from)} and founds the " +
          "${civName(civ)} at ${nameOf(town)}"
    )
    from.grudges.add(civ.index to civ.foundedEvent)
    civ.grudges.add(from.index to civ.foundedEvent)
  }

  /** The quarrel splits the civ: its most aggrieved far town takes the towns nearer to it than to the capital. */
  private fun splitOver(civ: Civ, year: Int, crisis: Int) {
    val standing = civ.towns.filter { towns[it].standing }
    if (standing.size < params.secessionMinTowns) return

    val leader = standing.filter { it != civ.capital }.maxByOrNull { grievanceOf(civ, towns[it]) } ?: return
    revolt(civ, towns[leader], year, causes = listOf(crisis), certain = true)
  }

  /** Two peoples at peace may bind their ruling houses by marriage, which cools what was building between them. */
  private fun arrangeMarriages(year: Int) {
    for (a in civs.indices) {
      for (b in a + 1 until civs.size) {
        val first = civs[a]
        val second = civs[b]
        if (!first.exists || !second.exists || first.ruler < 0 || second.ruler < 0) continue
        if (hostility.between(a, b) <= 0.0 || wars.any { it.involves(a, b) }) continue

        val bride = people[first.ruler]
        val groom = people[second.ruler]
        if (bride.spouse >= 0 || groom.spouse >= 0) continue
        if (roll(year.toLong(), a.toLong(), b.toLong(), MARRIAGE_SALT) >= params.marriageChance) continue

        val wedding = log(
          year, EventKind.MARRIAGE_ALLIANCE,
          listOf(Actor(ActorType.FIGURE, bride.index), Actor(ActorType.FIGURE, groom.index),
            Actor(ActorType.CIV, a), Actor(ActorType.CIV, b)),
          null, emptyList(),
          "${personName(bride)} of the ${civName(first)} weds ${personName(groom)} of the ${civName(second)}"
        )
        bride.spouse = groom.index
        groom.spouse = bride.index
        relations.add(RelationRecord(RelationKind.SPOUSE, bride.index, groom.index, wedding))
        hostility.set(a, b, hostility.between(a, b) * MARRIAGE_HOSTILITY_FACTOR)
      }
    }
  }

  /** The generals who faced each other may carry it on as a feud. */
  private fun maybeFeud(winner: Int, loser: Int, year: Int, battle: Int, near: Int) {
    val ours = people.firstOrNull { it.death == 0 && it.civ == winner && it.role == FigureRole.GENERAL } ?: return
    val theirs = people.firstOrNull { it.death == 0 && it.civ == loser && it.role == FigureRole.GENERAL } ?: return
    val pair = setOf(ours.index, theirs.index)
    if (relations.any { it.kind == RelationKind.RIVAL && setOf(it.from, it.to) == pair }) return
    if (roll(year.toLong(), ours.index.toLong(), theirs.index.toLong(), FEUD_SALT) >= params.feudChance) return

    val feud = log(
      year, EventKind.FEUD_BEGAN,
      listOf(Actor(ActorType.FIGURE, ours.index), Actor(ActorType.FIGURE, theirs.index),
        Actor(ActorType.CIV, winner), Actor(ActorType.CIV, loser)),
      towns[near].facts.position, listOf(battle),
      "${personName(ours)} and ${personName(theirs)} become sworn enemies after the battle near ${nameOf(towns[near])}"
    )
    relations.add(RelationRecord(RelationKind.RIVAL, ours.index, theirs.index, feud))
  }

  /** A battle won against the people an avenger swore against settles the oath. */
  private fun takeVengeance(winner: Int, loser: Int, year: Int, battle: Int, near: Int) {
    val settled = oaths.filter {
      people[it.avenger].death == 0 && people[it.avenger].civ == winner && it.against == loser
    }
    for (oath in settled) {
      log(
        year, EventKind.VENGEANCE_FULFILLED,
        listOf(Actor(ActorType.FIGURE, oath.avenger), Actor(ActorType.FIGURE, oath.slain), Actor(ActorType.CIV, loser)),
        towns[near].facts.position, listOf(oath.event, battle),
        "${personName(people[oath.avenger])} avenges ${personName(people[oath.slain])} on the ${civName(civs[loser])}"
      )
    }
    oaths.removeAll(settled)
  }

  /** Kin of a figure slain in battle may swear to avenge them on the people who won it. */
  private fun swearVengeance(slain: Person, winner: Int, year: Int, death: Int) {
    val avenger = people.firstOrNull { it.death == 0 && it.parent == slain.index }
      ?: people.getOrNull(slain.spouse)?.takeIf { it.death == 0 }
      ?: people.getOrNull(slain.parent)?.takeIf { it.death == 0 }
      ?: return
    if (roll(year.toLong(), slain.index.toLong(), VENGEANCE_SALT) >= params.vengeanceChance) return

    val oath = log(
      year, EventKind.VENGEANCE_SWORN,
      listOf(
        Actor(ActorType.FIGURE, avenger.index), Actor(ActorType.FIGURE, slain.index), Actor(ActorType.CIV, winner)
      ),
      towns[avenger.home].facts.position, listOf(death),
      "${personName(avenger)} swears to avenge ${personName(slain)} on the ${civName(civs[winner])}"
    )
    relations.add(RelationRecord(RelationKind.AVENGER, avenger.index, slain.index, oath))
    oaths.add(Oath(avenger.index, slain.index, winner, oath))
  }

  /**
   * A general of the besieged people opens the town's gates to the enemy instead of letting it burn. The town
   * changes hands unsacked, and the war is over. Returns whether it happened.
   */
  private fun betray(war: War, winner: Int, loser: Int, town: Town, year: Int, cause: Int): Boolean {
    val target = town.facts.index
    val traitor = people.firstOrNull {
      it.death == 0 && it.civ == loser && it.role == FigureRole.GENERAL
    } ?: return false
    if (roll(year.toLong(), target.toLong(), BETRAYAL_SALT) >= params.betrayalChance) return false

    transferTown(town, civs[loser], civs[winner])
    relocateCapital(civs[loser])
    val betrayal = log(
      year, EventKind.BETRAYAL,
      listOf(Actor(ActorType.FIGURE, traitor.index), Actor(ActorType.SETTLEMENT, target),
        Actor(ActorType.CIV, winner), Actor(ActorType.CIV, loser)),
      town.facts.position, listOf(cause),
      "${personName(traitor)} opens the gates of ${nameOf(town)} to the ${civName(civs[winner])}"
    )
    val betrayed = civs[loser].ruler
    if (betrayed >= 0) relations.add(RelationRecord(RelationKind.BETRAYED, traitor.index, betrayed, betrayal))
    war.decided = true
    return true
  }

  /** Roles are drawn against culture: a herding people produces generals, a mining people smiths. */
  private fun roleFor(civ: Civ, year: Int, rulerWanted: Boolean): FigureRole {
    val culture = civ.culture
    val weights = doubleArrayOf(
      if (rulerWanted) 1.0 else 0.0,  // RULER
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
    return if (rulerWanted) FigureRole.RULER else FigureRole.GENERAL
  }

  /** A general of the losing side may fall in the battle rather than in bed. */
  private fun killGeneralsOf(civ: Int, winner: Int, year: Int, cause: Int, where: Vec2d) {
    for (person in people.toList()) {
      if (person.death != 0 || person.civ != civ) continue
      if (person.role != FigureRole.GENERAL && person.role != FigureRole.RULER) continue
      if (roll(year.toLong(), person.index.toLong(), SLAIN_SALT) >= SLAIN_CHANCE) continue

      person.death = year
      person.slainAt = where
      val death = buryFigure(person, year, EventKind.FIGURE_SLAIN, "falls in battle", cause)
      swearVengeance(person, winner, year, death)
    }
  }

  /**
   * A dead figure gets a tomb if they were notable enough, and their artifact goes in with them.
   *
   * This is the join that makes an artifact findable. The provenance chain ends at a site, the site is a
   * marker in the world, and the marker is what a chunk materialises - so "the sword was lost with him at
   * Ashford" is a place a player can dig.
   */
  private fun buryFigure(person: Person, year: Int, kind: EventKind, how: String, cause: Int = -1): Int {
    val died = log(
      year, kind, listOf(Actor(ActorType.FIGURE, person.index), Actor(ActorType.CIV, person.civ)),
      person.slainAt ?: towns[person.home].facts.position,
      if (cause >= 0) listOf(cause) else emptyList(),
      "${personName(person)} $how"
    )

    if (civs[person.civ].ruler == person.index) succeed(civs[person.civ], person, year, died)

    // Half of what the dead carried goes into the grave; the rest passes on in `updateArtifacts`. A hero's trophy
    // always goes with the hero.
    val relic = relics.firstOrNull { it.holder == person.index }?.takeIf { relic ->
      monsters.any { it.trophy == relic.index } ||
          roll(year.toLong(), relic.index.toLong(), BURIAL_GOODS_SALT) < BURIAL_GOODS_CHANCE
    }
    val entombed = person.role == FigureRole.RULER || person.role == FigureRole.PROPHET || relic != null
    if (!entombed) return died

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
    return died
  }

  /** Who a dead holder's artifact passes to: their child, their spouse, their house, then anyone of their people. */
  private fun heirOf(holder: Person): Person? {
    val living = people.filter { it.death == 0 }
    return living.firstOrNull { it.parent == holder.index }
      ?: people.getOrNull(holder.spouse)?.takeIf { it.death == 0 }
      ?: living.firstOrNull { holder.dynasty >= 0 && it.dynasty == holder.dynasty }
      ?: living.firstOrNull { it.civ == holder.civ }
  }

  /** The capital of the dead holder's people, while it stands and they still hold it. */
  private fun shrineFor(holder: Person): Town? {
    val civ = civs[holder.civ]
    val capital = towns[civ.capital]
    return capital.takeIf { civ.exists && it.standing && it.owner == civ.index }
  }

  /** Nobody takes it up, so the capital's temple keeps it. */
  private fun enshrine(relic: Relic, town: Town, year: Int) {
    relic.holder = -1
    relic.keptAt = town.facts.index
    if (relic.homeTemple < 0) relic.homeTemple = town.facts.index

    val god = deities.getOrNull(town.patron)?.let { Names.deity(it.nameSeed, town.facts.cultureIndex, it.domain) }
    relic.provenance.add(
      log(
        year, EventKind.ARTIFACT_ENSHRINED,
        listOf(Actor(ActorType.ARTIFACT, relic.index), Actor(ActorType.SETTLEMENT, town.facts.index)),
        town.facts.position, emptyList(),
        "${relicName(relic)} is given to the ${god?.let { "temple of $it" } ?: "treasury"} at ${nameOf(town)}"
      )
    )
  }

  /** The victors carry what the conquered town kept home to their own capital. */
  private fun plunder(town: Town, winner: Civ, year: Int, conquest: Int) {
    val capital = towns[winner.capital].takeIf { it.standing && it.owner == winner.index } ?: return
    if (capital === town) return

    for (relic in relics) {
      if (relic.keptAt != town.facts.index) continue
      if (roll(year.toLong(), relic.index.toLong(), PLUNDER_SALT) >= PLUNDER_CHANCE) continue

      relic.keptAt = capital.facts.index
      relic.provenance.add(
        log(
          year, EventKind.ARTIFACT_PLUNDERED,
          listOf(
            Actor(ActorType.ARTIFACT, relic.index), Actor(ActorType.SETTLEMENT, capital.facts.index),
            Actor(ActorType.CIV, winner.index)
          ),
          capital.facts.position, listOf(conquest),
          "the ${civName(winner)} carry ${relicName(relic)} off from ${nameOf(town)} to ${nameOf(capital)}"
        )
      )
    }
  }

  /** What a town kept stays in its ruin. */
  private fun loseKeptRelics(town: Town, ruin: Int, year: Int, ending: Int) {
    for (relic in relics) {
      if (relic.keptAt != town.facts.index) continue

      relic.keptAt = -1
      relic.resting = ruin
      relic.provenance.add(
        log(
          year, EventKind.ARTIFACT_LOST,
          listOf(Actor(ActorType.ARTIFACT, relic.index), Actor(ActorType.SITE, ruin)),
          sites[ruin].position, listOf(ending),
          "${relicName(relic)} is lost in the ruin of ${nameOf(town)}"
        )
      )
    }
  }

  private fun updateArtifacts(year: Int) {
    // Smiths forge; rulers commission. Either way it takes a figure, which is what ties an artifact to a
    // person and a place instead of leaving it a loot table entry.
    for (person in people.toList()) {
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

      val heir = heirOf(holder)
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
      } else if (shrineFor(holder) != null && roll(year.toLong(), relic.index.toLong(), ENSHRINE_SALT) < ENSHRINE_CHANCE) {
        enshrine(relic, shrineFor(holder)!!, year)
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

  /**
   * Somebody carries the treasury out of a burning town, into a cave, and does not come back for it.
   *
   * **This is why hoards exist as history rather than as loot.** Nearly every real hoard was buried by someone
   * who fully intended to dig it up again, and is findable today precisely because they were killed before
   * they could - so the interesting object is not the treasure, it is the *reason nobody collected it*. That
   * reason is already in the log: the sacking is passed in as the cause, so the chain reads "the
   * Anlaf sack their town, somebody hides the plate in the caves above it, and that is the last of it".
   *
   * Placement is terrain's, not history's - `SpecialSiteCandidates.caves` found the back of every cave and
   * this only decides whether anybody ever had reason to run there. Each cave takes one hoard: two hoards in
   * one cave is two quests in the same room.
   *
   * An artifact goes in only if the town actually held one. Most hoards are coin and plate, which is right -
   * a world where every hidden hoard contains a named sword has no named swords.
   */
  private fun hideHoard(town: Town, index: Int, year: Int, cause: Int) {
    if (candidates.caves.isEmpty()) return
    if (roll(year.toLong(), index.toLong(), HOARD_SALT) >= params.hoardChance) return

    val cave = candidates.caves
      .filter { it.detail !in usedCaves }
      .minByOrNull { it.position.distanceTo(town.facts.position) }
      ?: return
    if (cave.position.distanceTo(town.facts.position) > params.hoardRange) return
    usedCaves.add(cave.detail)

    // Whatever was in the town to carry. `holder` is still set, so this is a relic in use rather than one
    // already lost, which is what makes the loss a moment rather than a bookkeeping entry.
    //
    // A capital counts as holding its civ's relics as well as its own. Without that clause the condition is
    // "a living smith or ruler happened to be homed in exactly this town", which over a whole reference world
    // came up **zero times in ten hoards** - so the artifact half of this existed and was never once reached,
    // which is indistinguishable from it not working.
    val capitalOf = if (town.owner >= 0 && civs[town.owner].capital == index) town.owner else -1
    val relic = relics.firstOrNull { it.keptAt == index } ?: relics.firstOrNull {
      it.holder >= 0 && it.resting < 0 &&
          (people[it.holder].home == index || (capitalOf >= 0 && people[it.holder].civ == capitalOf))
    }

    val site = addSite(
      SiteKind.HOARD, cave.position, year, index, town.owner,
      radius = HOARD_RADIUS, artifact = relic?.index ?: -1, figure = -1,
      elevation = cave.elevation
    )
    town.sites.add(site)

    val hidden = log(
      year, EventKind.ARTIFACT_LOST,
      listOf(Actor(ActorType.SETTLEMENT, index), Actor(ActorType.SITE, site)),
      cave.position, listOf(cause),
      "the wealth of ${nameOf(town)} is carried into the caves and hidden"
    )

    if (relic != null) {
      relic.holder = -1
      relic.keptAt = -1
      relic.resting = site
      relic.provenance.add(
        log(
          year, EventKind.ARTIFACT_LOST,
          listOf(Actor(ActorType.ARTIFACT, relic.index), Actor(ActorType.SITE, site)),
          cave.position, listOf(hidden),
          "${relicName(relic)} goes into the dark with it"
        )
      )
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
    // A people sworn to Chaos, working ground the mana already reached, make things out of it. The gate is
    // both clauses: the conviction alone is not enough, because the material has to have come from somewhere,
    // and a Chaos city on clean ground has nothing to quench a blade in.
    civ.sworn == Faction.CHAOS && town.facts.mana >= params.blightMana -> "crystal and blackened iron"
    town.facts.resourceValue > 0.6 && civ.technology > 0.5 -> "steel and silver"
    civ.technology > 0.35 -> "wrought iron"
    else -> "bronze"
  }

  // --- Monuments and walls ---------------------------------------------------------------------------

  private fun raiseMonuments(year: Int) {
    for (civ in civs.toList()) {
      if (!civ.exists || civ.technology < MONUMENT_TECHNOLOGY) continue
      val capital = civ.towns.filter { towns[it].standing }.maxByOrNull { towns[it].population } ?: continue
      val town = towns[capital]
      if (town.population < MONUMENT_POPULATION) continue
      if (town.sites.any { sites[it].kind == SiteKind.MONUMENT }) continue
      if (roll(year.toLong(), civ.index.toLong(), MONUMENT_SALT) >= MONUMENT_CHANCE) continue

      // The town's most precious keepsake may be sealed into the stone.
      val sealed = relics.firstOrNull { it.keptAt == capital }
        ?.takeIf { roll(year.toLong(), it.index.toLong(), MONUMENT_RELIC_SALT) < MONUMENT_RELIC_CHANCE }

      val site = addSite(
        SiteKind.MONUMENT, offset(town.facts.position, capital.toLong(), MONUMENT_OFFSET),
        year, capital, civ.index, MONUMENT_RADIUS, artifact = sealed?.index ?: -1, figure = -1
      )
      town.sites.add(site)

      val built = log(
        year, EventKind.MONUMENT_BUILT,
        listOf(Actor(ActorType.SITE, site), Actor(ActorType.CIV, civ.index)),
        sites[site].position, emptyList(),
        "the ${civName(civ)} raise ${siteName(sites[site])} at ${nameOf(town)}"
      )

      if (sealed != null) {
        sealed.keptAt = -1
        sealed.resting = site
        sealed.provenance.add(
          log(
            year, EventKind.ARTIFACT_SEALED,
            listOf(Actor(ActorType.ARTIFACT, sealed.index), Actor(ActorType.SITE, site)),
            sites[site].position, listOf(built),
            "${relicName(sealed)} is sealed into ${siteName(sites[site])}"
          )
        )
      }
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
    for (civ in civs.toList()) {
      if (!civ.exists) continue

      buildSite(
        year, civ, SiteKind.MINE, candidates.mines, params.mineTechnology, MINE_SALT,
        EventKind.MINE_OPENED, MINE_RADIUS
      ) { candidate ->
        // A mine belongs to whichever of this civ's standing towns is near enough to work it.
        nearestStandingTown(civ, candidate.position, params.mineRange) != null
      }

      buildSite(
        // The Circle's discipline is records and instruments, and a religious house is where a pre-industrial
        // people keeps both. Note what this deliberately does *not* do: it does not rename the building for
        // them. An "observatory" that is the same voxels as an abbey would be two words for one structure, and
        // the Circle already has a building of its own - the stone circle its shrine raises.
        year, civ, SiteKind.MONASTERY, candidates.monasteries,
        eased(civ, params.monasteryTechnology, Faction.CIRCLE), MONASTERY_SALT,
        EventKind.MONASTERY_FOUNDED, MONASTERY_RADIUS
      ) { candidate ->
        // Remoteness is the site's defining property and was already enforced against *every* settlement when
        // the candidate was found. What is left is reach: a civ does not found a house it cannot walk to.
        nearestStandingTown(civ, candidate.position, params.fortRange) != null
      }

      buildSite(
        // Eternity holds lines, and a fort is the most literal line a pre-industrial people can hold.
        year, civ, SiteKind.FORT, candidates.forts,
        eased(civ, params.fortTechnology, Faction.ETERNITY), FORT_SALT,
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
   * A technology gate, brought forward for the Order whose own work the building is.
   *
   * A *gate* rather than a chance, which is what makes this the cheap way to express an Order's priorities: it
   * changes **when** in a thousand years a people gets round to something, not whether they can. So a sworn
   * civilisation's forts and abbeys arrive a century or two earlier than its neighbours', and an unaligned
   * world builds exactly as many as it did before the Orders existed.
   *
   * Floored at zero, so easing a gate that is already low is a no-op rather than a negative threshold that
   * every civ passes from year one.
   */
  private fun eased(civ: Civ, technology: Double, favoured: Faction): Double =
    if (civ.sworn == favoured) max(0.0, technology - ORDER_TECHNOLOGY_EASE) else technology

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
    // Not built by `buildSites`, which is what this verb table serves - a shrine is raised by `raiseShrines`
    // and logs its own sentence, because an Order's reason for building one is the whole point of the line.
    SiteKind.SHRINE, SiteKind.SANCTUARY, SiteKind.SEAL -> "raise"
    // The residue kinds are never built by this pass; the branch exists so adding a kind is a compile error.
    SiteKind.RUIN, SiteKind.ASH_RUIN, SiteKind.BATTLEFIELD, SiteKind.TOMB, SiteKind.MONUMENT, SiteKind.HOARD,
    SiteKind.WOUND, SiteKind.LAIR -> "make"
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
    for (civ in civs.toList()) {
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
    val threads = QuestThreads(chronicleOf(events, pruned = 0, threads = emptyList())).all()
    val kept = prune(threads)

    return chronicleOf(kept, prunedEvents, threads)
  }

  private fun chronicleOf(log: List<HistoryEvent>, pruned: Int, threads: List<ThreadRecord>): Chronicle {
    return Chronicle(
      startYear = startYear,
      presentYear = presentYear,
      events = log,
      civs = civs.map { civ ->
        CivRecord(
          index = civ.index, cultureIndex = civ.cultureIndex, nameSeed = civ.nameSeed,
          foundedYear = civ.founded, endedYear = civ.ended, capital = civ.capital,
          technology = civ.technology, peakPopulation = civ.peak,
          settlements = civ.towns.sorted(), grudges = civ.grudges.toList(),
          sworn = civ.sworn, swornYear = civ.swornYear, origin = civ.origin, parent = civ.parent,
          patron = civ.patron
        )
      },
      figures = people.map {
        FigureRecord(
          index = it.index, nameSeed = it.nameSeed, role = it.role, civ = it.civ,
          homeSettlement = it.home, birthYear = it.birth, deathYear = it.death,
          restingSite = it.resting, parent = it.parent, dynasty = it.dynasty, spouse = it.spouse, sworn = it.sworn
        )
      },
      artifacts = relics.map {
        ArtifactRecord(
          index = it.index, nameSeed = it.nameSeed, kind = it.kind, forgedYear = it.forgedYear,
          forgedBy = it.forgedBy, material = it.material, forgedAtNameSeed = it.forgedAtNameSeed,
          restingSite = it.resting, provenance = it.provenance.toList(),
          holder = it.holder.takeIf { holder -> holder >= 0 && people[holder].death == 0 } ?: -1,
          keptAt = it.keptAt, homeTemple = it.homeTemple
        )
      },
      sites = sites.map {
        SiteRecord(
          index = it.index, kind = it.kind, position = it.position, year = it.year,
          settlement = it.settlement, civ = it.civ, radius = it.radius,
          decay = decayOf(it), nameSeed = it.nameSeed, artifact = it.artifact, figure = it.figure,
          resource = it.resource, elevation = it.elevation, faction = it.faction,
          battles = it.battles, fallen = it.fallen, cult = it.cult, monster = it.monster
        )
      },
      settlements = towns.map { town ->
        SettlementRecord(
          index = town.facts.index, foundedYear = town.founded, abandonedYear = town.abandoned,
          population = town.population.toInt(), wealth = town.wealth.coerceIn(0.0, 1.0),
          ownerCiv = town.owner, foundingCiv = town.foundingCiv, timesSacked = town.sacked,
          wallYear = town.wallYear, wallPopulation = town.wallPopulation,
          nameSeed = town.nameSeed, oldNameSeed = town.oldNameSeed, ruinCause = town.ruinCause,
          sites = town.sites.toList(), patron = town.patron
        )
      },
      prunedEvents = pruned,
      dynasties = dynasties.map {
        DynastyRecord(
          index = it.index, nameSeed = it.nameSeed, founder = it.founder, civ = it.civ, seat = it.seat,
          foundedYear = it.founded, endedYear = it.ended, exiledTo = it.exiledTo
        )
      },
      relations = relations.toList(),
      deities = deities.map { DeityRecord(it.index, it.domain, it.nameSeed, it.year, it.cause) },
      monsters = monsters.filter { it.fate != null }.map {
        MonsterRecord(
          index = it.index, boss = it.boss, nameSeed = it.nameSeed, origin = it.origin, originEvent = it.originEvent,
          awokeYear = it.awoke, endedYear = it.ended, fate = it.fate!!, hero = it.hero, site = it.site,
          trophy = it.trophy
        )
      },
      cults = cults.map {
        CultRecord(
          index = it.index, nameSeed = it.nameSeed, kind = it.kind, deity = it.deity, beast = it.beast,
          founder = it.founder, origin = it.origin, foundedYear = it.founded, endedYear = it.ended, fate = it.fate,
          settlements = it.towns.toList(), leaning = it.leaning, sanctuary = it.sanctuary, realm = it.realm
        )
      },
      threads = threads
    )
  }

  /**
   * Importance pruning: keep everything notable, sample the rest, and never break a causal chain.
   *
   * The last clause is the one that takes care. Dropping an event that a surviving event cites as a cause
   * leaves a dangling id, and a provenance chain with a hole in it is worse than a shorter chain - so
   * anything referenced by a keeper is kept regardless of its own importance, and so is every event on an
   * artifact's chain or that a thread stays open since.
   */
  private fun prune(threads: List<ThreadRecord>): List<HistoryEvent> {
    val keep = LinkedHashSet<Int>()

    for (event in events) {
      val notable = event.importance >= params.importanceFloor
      val sampled = event.id % params.minorEventSampling == 0
      if (notable || sampled) keep.add(event.id)
    }
    for (relic in relics) keep.addAll(relic.provenance)
    for (thread in threads) keep.add(thread.since)

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
    resource: Int = -1,
    elevation: Double = Double.NaN,
    faction: Faction? = null,
    cult: Int = -1,
    monster: Int = -1
  ): Int {
    val index = sites.size
    sites.add(
      Site(
        index = index, kind = kind, position = position, year = year, settlement = settlement,
        civ = civ, radius = radius,
        nameSeed = Names.seedOf(worldSeed, SITE_NAME_SALT, index.toLong()),
        artifact = artifact, figure = figure, resource = resource, elevation = elevation, faction = faction,
        cult = cult, monster = monster
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

  /**
   * One element of a list, chosen by a keyed roll on `(year, civ, salt)`. Null for an empty list.
   *
   * Written for the rites, and the reason is legibility rather than mechanics. `firstOrNull` was correct and
   * read terribly: a Circle people with four stone circles took the reckoning at the *same* one twenty times
   * over a thousand years, so `chronicle -Porders` printed eight identical sentences and the world looked like
   * it had one shrine. Varying the place costs a roll and turns a repeated line into a history.
   */
  private fun <T> List<T>.pick(year: Int, civ: Civ, salt: Long): T? =
    if (isEmpty()) null else this[(roll(year.toLong(), civ.index.toLong(), salt) * size).toInt().coerceIn(indices)]

  private fun nameOf(town: Town) = Names.place(town.nameSeed, town.facts.cultureIndex)

  private fun civName(civ: Civ) = Names.civ(civ.nameSeed, civ.cultureIndex)

  private fun houseName(house: Dynasty) = Names.house(house.nameSeed, civs[house.civ].cultureIndex)

  private fun personName(person: Person) =
    Names.person(person.nameSeed, civs[person.civ].cultureIndex, person.role)

  private fun relicName(relic: Relic) = Names.artifact(
    relic.nameSeed, civs[people[relic.forgedBy].civ].cultureIndex, relic.kind, relic.forgedAtNameSeed
  )

  private fun siteName(site: Site): String {
    val of = when {
      site.monster >= 0 && (site.kind == SiteKind.LAIR || site.kind == SiteKind.SEAL) ->
        monsterName(monsters[site.monster])
      site.settlement >= 0 -> nameOf(towns[site.settlement])
      else -> "the wilds"
    }
    return Names.site(
      site.nameSeed, of,
      when (site.kind) {
        SiteKind.RUIN -> "ruin"
        // `Names.site` renders an unknown form as "the <form> of <Town>", so this reads "the ash of Karth" with
        // no edit to `Names` at all.
        SiteKind.ASH_RUIN -> "ash"
        SiteKind.BATTLEFIELD -> "field"
        SiteKind.TOMB -> "barrow"
        SiteKind.MONUMENT -> "monument"
        // `Names.site` ends in `else -> "the $form of $of"`, so a new form needs no edit to `Names` at all -
        // which is the mechanism that makes the lore for these four free.
        SiteKind.MINE -> "mine"
        SiteKind.MONASTERY -> "abbey"
        SiteKind.FORT -> "fort"
        SiteKind.LIGHTHOUSE -> "light"
        SiteKind.HOARD -> "hoard"
        SiteKind.WOUND -> "wound"
        SiteKind.SANCTUARY -> "sanctuary"
        SiteKind.SEAL -> "seal"
        SiteKind.LAIR -> "lair"
        // Three forms from one kind, off the same `else` branch in `Names.site`. This is the clearest thing the
        // order-on-a-channel design buys: "the cairn of Ashford" and "the ward of Ashford" are different
        // places to a player, and neither needed a `SiteKind` or a word pool of its own.
        SiteKind.SHRINE -> when (site.faction) {
          Faction.CHAOS -> "cairn"
          Faction.ETERNITY -> "ward"
          Faction.CIRCLE -> "circle"
          // Unreachable: `raiseShrines` is the only producer and it always sets one. Named rather than
          // thrown because a name is not worth failing a world's generation over.
          null -> "shrine"
        }
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

    /** Failed harvests blamed on the mana. Stops accruing once [warded]; see `resolveMana`. */
    var blights = 0
    var warded = false

    /**
     * Year the wards went up, or 0.
     *
     * Kept on the town rather than only in the log because it is the same shape of fact as [wallYear] - and,
     * like the walls, it is what explains a town still being there.
     */
    var wardYear = 0
    var nameSeed = 0L
    var oldNameSeed = 0L
    var ruinCause: EventKind? = null
    val sites = ArrayList<Int>()

    /** The god its temple is dedicated to: its founders' patron. Kept when the town changes hands. */
    var patron = -1

    val standing get() = founded != 0 && abandoned == 0
  }

  /** Symmetric pairwise hostility between civs, grown one row and column per [addCiv]. */
  private class Hostility {
    private var cells = Array(0) { DoubleArray(0) }

    fun addCiv() {
      val old = cells
      val size = old.size + 1
      cells = Array(size) { a -> old.getOrNull(a)?.copyOf(size) ?: DoubleArray(size) }
    }

    fun between(a: Int, b: Int): Double {
      return cells[a][b]
    }

    fun set(a: Int, b: Int, value: Double) {
      cells[a][b] = value
      cells[b][a] = value
    }
  }

  private class Civ(
    val index: Int,
    val cultureIndex: Int,
    /** Moves to the largest standing town when the capital falls or empties. See [relocateCapital]. */
    var capital: Int,
    val origin: CivOrigin,
    val parent: Int
  ) {
    var nameSeed = 0L

    /** Figure index of the living ruler, or -1 between one ruler's death and the next one's rise. */
    var ruler = -1

    /** Index of the ruling house, or -1 before the first ruler. */
    var house = -1

    /** Index of the patron god, or -1 on a world with no gods. */
    var patron = -1
    var founded = 0
    var ended = 0
    var technology = 0.0
    var peak = 0
    val towns = ArrayList<Int>()
    val grudges = ArrayList<Pair<Int, Int>>()

    /** Year this civ last built each kind of site, so they arrive across a history instead of all at once. */
    val lastBuilt = HashMap<SiteKind, Int>()

    /** Event id of its founding, or -1. Cited by its oath, so a sworn people threads back to who they are. */
    var foundedEvent = -1

    /** The Order it holds now, or null while unaligned. See `swearOrders`. */
    var sworn: Faction? = null
    var swornYear = 0

    /** Event id of the latest oath or schism, or -1. What a rite and the next schism cite. */
    var swornEvent = -1

    /** How many times it has changed its Order. Capped - see `MAX_SCHISMS`. */
    var schisms = 0

    /** Towns of its own given up to the blight since it swore. See `contradicted`. */
    var townsLostSinceOath = 0

    /** World-wide blight losses at the moment it swore, so Chaos can ask whether anything has ended since. */
    var blightLossesAtOath = 0

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

    /** The Order they held, usually their civ's. Null on an unaligned world or an unaligned people. */
    var sworn: Faction? = null

    var parent = -1
    var dynasty = -1
    var spouse = -1
  }

  private class Dynasty(
    val index: Int,
    val nameSeed: Long,
    val founder: Int,
    val civ: Int,
    val seat: Int,
    val founded: Int
  ) {
    var ended = 0
    var exiledTo = -1
  }

  private class Oath(val avenger: Int, val slain: Int, val against: Int, val event: Int)

  private class Monster(val index: Int, val boss: WorldBoss, val nameSeed: Long, val awakens: Int) {
    var awoke = 0
    var awokeEvent = -1
    var origin = MonsterOrigin.ANCIENT
    var originEvent = -1
    var position = Vec2d(0.0, 0.0)
    var resolves = 0
    var ended = 0
    var fate: MonsterFate? = null
    var hero = -1
    var site = -1
    var trophy = -1
  }

  private class Deity(val index: Int, val domain: Domain, val nameSeed: Long, val year: Int, val cause: Int)

  private class Cult(
    val index: Int,
    val nameSeed: Long,
    val kind: CultKind,
    val deity: Int,
    val beast: BeastKind?,
    val founder: Int,
    val origin: Int,
    val founded: Int,
    val leaning: Faction?,
    val sanctuary: Int
  ) {
    val towns = LinkedHashSet<Int>()
    var ended = 0
    var fate = CultFate.ACTIVE
    var realm = -1
    val active get() = fate == CultFate.ACTIVE
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

    /** The settlement whose temple or treasury keeps it, or -1. */
    var keptAt = -1

    /** The settlement that first kept it, or -1. It wants the artifact back wherever it went later. */
    var homeTemple = -1
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
    val resource: Int = -1,
    /** Metres, for a site that is not on the ground. NaN for every kind but a hoard. */
    val elevation: Double = Double.NaN,
    /** The Order that raised it, for a shrine. Null for every other kind. */
    val faction: Faction? = null,
    /** The cult whose sanctuary this is, or -1. */
    val cult: Int = -1,
    /** The world boss whose seal, lair or slaying field this is, or -1. */
    val monster: Int = -1
  ) {
    /** Battles fought here; more than one when a later battle came within the merge radius. Battlefields only. */
    var battles = 0
    var fallen = 0
  }

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
     * Chance **per vent per tick** that a volcano erupts, scaled by its strength.
     *
     * A different sentence from the one that used to be here, and the whole paragraph is rewritten rather than
     * retuned because leaving the old one would make it a lie about the code. It used to be the chance per *town*
     * per tick of that town being buried, and the note recorded that 0.004 gave a chronicle with "thirty-five
     * towns buried in ash against two razed by war" - a world whose ruins were geology rather than history.
     *
     * That measurement was about the **town-destruction** rate, and this number is no longer that. An eruption
     * now happens to a mountain and may bury nothing at all: [BURIAL_CHANCE] is what decides whether it takes
     * anybody with it. So this can be a great deal larger than 0.0004 without repeating the mistake - at 0.02 a
     * vent wakes about four times over two hundred ticks, which is what makes an eruption something the chronicle
     * of a *region* records rather than a once-per-world curiosity.
     *
     * If the ash ruins ever come back too numerous, [BURIAL_CHANCE] is the lever and not this one. Lowering this
     * makes volcanoes quiet; lowering that makes them survivable, and the second is what the brief asks for.
     */
    const val ERUPTION_RATE = 0.02

    /**
     * Chance that a town at the vent itself is buried by one eruption, tapering to zero at [ASH_REACH].
     *
     * The lever that decides how many ash ruins a world has, and separate from [ERUPTION_RATE] on purpose - see
     * that KDoc. A town on the flank is in real danger and one at the edge of the reach is mostly not, which is
     * what leaves survivors with an eruption in living memory.
     */
    const val BURIAL_CHANCE = 0.22

    /**
     * How far ash from an eruption reaches, in metres.
     *
     * **Raw, not scaled by world length.** Ash fall from a cone is a real distance - a plume rises to a real
     * height and falls out over real kilometres, whatever size the map is - which is `manaField`'s own argument
     * about `blightRange` one file over. Worth noting that the eruption gate had exactly the opposite defect
     * before this: the old `HistoryStage.volcanismField` scaled its 40 km reach, so on a small world a town was
     * "on an arc" at ten kilometres from the fault and on a large one at eighty.
     */
    const val ASH_REACH = 18_000.0

    const val FORGE_CHANCE = 0.30
    const val INHERIT_CHANCE = 0.55

    /** Of the artifacts nobody inherits, how many the capital's temple takes in rather than losing. */
    const val ENSHRINE_CHANCE = 0.5
    const val BURIAL_GOODS_CHANCE = 0.5
    const val PLUNDER_CHANCE = 0.6
    const val MONUMENT_RELIC_CHANCE = 0.5

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

    /**
     * Extent of a hoard in metres, which is small on purpose: it is a pile against a wall, not a chamber.
     *
     * It also has to stay under `ChunkMaterializer.MARKER_MARGIN` like every other site marker, which the
     * invariant checks - a point marker wider than the chunk query margin is simply absent from chunks
     * further away than it.
     */
    const val HOARD_RADIUS = 3.0

    const val TOMB_RADIUS = 11.0
    const val TOMB_OFFSET = 320.0
    const val BATTLEFIELD_RADIUS = 180.0

    // --- The mana ---
    //
    // Rates rather than thresholds, which is why these are here and not in `HistoryParams` - the same division
    // `PLAGUE_RATE` and `ERUPTION_RATE` already sit on.

    /**
     * Share of the span within which the star falls: the first tenth.
     *
     * Early so that the rest of the history happens in a world that already has mana in it. A star that fell in
     * year 900 of 1000 would leave the chronicle with one line about it and nothing that followed.
     */
    const val STAR_WINDOW_SHARE = 10

    /**
     * Blight chance per tick at full severity, before the ward relief.
     *
     * Between `FLOOD_RATE` and `FAMINE_RATE`, deliberately: a failed harvest is what this *is*, and the blight
     * should not be more common than ordinary bad luck. Over two hundred ticks it takes a town on the interior
     * edge of a province past [HistoryParams.blightsBeforeForsaking] and leaves one on the fringe with a line
     * or two in the log and its people still there.
     */
    const val BLIGHT_RATE = 0.10

    /** Share of a town's people a blight costs, plus this much again scaled by how bad the ground is. */
    const val BLIGHT_TOLL = 0.06
    const val BLIGHT_TOLL_SPREAD = 0.10

    /** What a blight does to a town's wealth. Harsher than a flood: the fields do not come back. */
    const val BLIGHT_WEALTH_LOSS = 0.80

    /**
     * What one blight permanently costs a settlement's carrying capacity.
     *
     * Gentler than `SACK_DECLINE` (0.82) on purpose: an army burns a town once and a blight comes back, so at
     * 0.92 compounded over the handful of blights a town survives the two land in the same place. Capacity has
     * its own floor in `capacityOf`, so no amount of compounding can drive a town to nothing this way.
     */
    const val BLIGHT_DECLINE = 0.92

    /**
     * Population no blight will take a settlement below.
     *
     * Comfortably above `growPopulations`' twelve-person dwindling threshold, with room for the toll to be
     * computed against a larger number and still land above it. The long comment at the call site is the reason
     * this exists at all, and it is worth reading before changing the number.
     */
    const val BLIGHT_FLOOR = 20.0

    /** Multiplier on the blight rate once the wards are up. Not zero - a ward is a defence, not a cure. */
    const val WARD_RELIEF = 0.25

    /**
     * Blights a town suffers before it wards itself.
     *
     * Two, for the reason [buildWalls] gives about walls: the interesting thing about a defence is that it dates
     * from the second time somebody needed it. It must stay below
     * [HistoryParams.blightsBeforeForsaking] or nothing is ever warded.
     */
    const val WARD_AFTER_BLIGHTS = 2

    /**
     * Radius of a wound in metres, and the widest structural marker in the world.
     *
     * Under `ChunkMaterializer.MARKER_MARGIN` (320 m) like every other site marker, and much closer to it than
     * anything else - which is why `Invariants.checkStructuralMarkersFitTheQueryMargin` lists `WOUND`. Sharing
     * the ceiling with [MAX_RUIN_RADIUS] rather than reading it, on the same duplicate-with-a-tripwire argument
     * recorded there: `history` is a sibling of `voxel` and siblings do not call into each other.
     */
    const val WOUND_RADIUS = 260.0

    /** Metres from a wound's centre a seer's relic ends up. Outside the crystal field, on its rim. */
    const val WOUND_RELIC_OFFSET = 300.0

    /**
     * Chance per tick that a prophet or scholar within reach of a wound goes out to it and is lost.
     *
     * Comparable to `SLAIN_CHANCE`, so going out to the wound is about as dangerous as being a general in a war
     * - which is the note this is meant to strike. Only ever rolled for two of the six roles.
     */
    const val SEER_LOSS_CHANCE = 0.18

    /**
     * Chance per tick that an eligible figure is lost in the deepest waste, before the harshness scaling.
     *
     * Under [SEER_LOSS_CHANCE] on purpose, and the reason is the difference between the two events: a seer
     * *chooses* to walk out to a wound, which is close to a decision, while this is somebody who set out
     * intending to arrive. It is also multiplied by the candidate's quality, so the badlands take about 0.7 of
     * this and the deep desert all of it - meaning the figure this produces is a few graves per world rather
     * than a scattering along every dune field.
     */
    const val TRAVELLER_LOSS_CHANCE = 0.10

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
    //
    // `MONASTERY_SALT` was **0x26, the same value as `HOARD_SALT`**, which is the exact failure the paragraph
    // above says these constants exist to prevent. Both are rolled as `roll(year, index, salt)` with the same
    // arity, so in any year where a town index happened to equal a civ index the two decisions read the same
    // number - and since `hoardChance` (0.30) is five times `builtSiteChance` (0.06), every monastery that civ
    // founded landed in a year that civ's like-numbered town was also eligible to hide a hoard. Nothing looked
    // wrong on a map and no test could have found it; it was two constants that had to differ and did not.
    const val MINE_SALT = 0x25L
    const val HOARD_SALT = 0x26L
    const val FORT_SALT = 0x27L
    const val LIGHTHOUSE_SALT = 0x28L
    const val MONASTERY_SALT = 0x29L

    // The mana. Each of the three is an independent decision about a different subject - the world, a town, a
    // person - so all three need their own.
    const val STAR_SALT = 0x2AL
    const val BLIGHT_SALT = 0x2BL
    const val SEER_SALT = 0x2CL
    const val TRAVELLER_SALT = 0x35L

    /**
     * Whether one eruption buries one town, per year.
     *
     * A second salt beside `ERUPTION_SALT` rather than a reuse, and read the `MONASTERY_SALT` story above before
     * touching either. `ERUPTION_SALT` is now rolled as `roll(year, ventIndex)` and this as
     * `roll(year, ventIndex, townIndex)` - different arities, so they would be different streams even sharing a
     * value. They do not share one anyway, because relying on arity is relying on `roll`'s implementation.
     */
    const val ASHFALL_SALT = 0x2DL

    /** Names a volcano. Separate from `PLACE_NAME_SALT` so a vent and a town of the same index differ. */
    const val VENT_NAME_SALT = 0x06L
    const val HOUSE_NAME_SALT = 0x07L
    const val DEITY_NAME_SALT = 0x08L
    const val CULT_NAME_SALT = 0x09L
    const val MONSTER_NAME_SALT = 0x0AL

    // The Orders. Six independent decisions about the same subject - a civilisation - so all six need their
    // own value, and read the `MONASTERY_SALT` story above before touching any of them. `ORDER_SHARE_SALT` is
    // the one to be most careful with: it is rolled as `roll(civIndex, salt)` with no year in it, which is
    // deliberate (see `maySwear`) and means it is the only salt here whose stream is one draw per civ for the
    // whole simulation rather than one per tick.
    const val ORDER_SHARE_SALT = 0x2EL
    const val ORDER_SWEAR_SALT = 0x2FL
    const val ORDER_DRAW_SALT = 0x30L
    const val SCHISM_SALT = 0x31L
    const val SCHISM_DRAW_SALT = 0x32L
    const val RITE_SALT = 0x33L
    const val SHRINE_SALT = 0x34L

    /** Which of the eligible places a rite happens at. Separate from `RITE_SALT`, which decides *whether*. */
    const val RITE_PLACE_SALT = 0x36L

    const val ARRIVAL_SALT = 0x37L
    const val MIGRATION_SALT = 0x38L
    const val NEWCOMER_CULTURE_SALT = 0x39L
    const val REVOLT_SALT = 0x3AL
    const val REVOLT_OUTCOME_SALT = 0x3BL
    const val REFUGEE_SALT = 0x3CL
    const val FRONT_SALT = 0x3DL
    const val GROUND_ALONG_SALT = 0x3EL
    const val GROUND_ASIDE_SALT = 0x3FL
    const val OLD_GROUND_SALT = 0x40L
    const val CHILD_SALT = 0x41L
    const val PARENT_PICK_SALT = 0x42L
    const val HEIR_SALT = 0x43L
    const val CRISIS_SALT = 0x44L
    const val MARRIAGE_SALT = 0x45L
    const val FEUD_SALT = 0x46L
    const val VENGEANCE_SALT = 0x47L
    const val BETRAYAL_SALT = 0x48L
    const val DEITY_DRAW_SALT = 0x49L
    const val DEITY_EXTRA_SALT = 0x4AL
    const val PATRON_SALT = 0x4BL
    const val CULT_SALT = 0x4CL
    const val CULT_KIND_SALT = 0x4DL
    const val CULT_PICK_SALT = 0x4EL
    const val CULT_SPREAD_SALT = 0x4FL
    const val CULT_PERSECUTION_SALT = 0x50L
    const val CULT_OUTCOME_SALT = 0x51L
    const val CULT_ABSORB_SALT = 0x52L
    const val MONSTER_EXTRA_SALT = 0x53L
    const val MONSTER_ORDER_SALT = 0x54L
    const val MONSTER_WAKE_SALT = 0x55L
    const val MONSTER_REIGN_SALT = 0x56L
    const val RAVAGE_SALT = 0x57L
    const val MONSTER_FATE_SALT = 0x58L
    const val ENSHRINE_SALT = 0x59L
    const val PLUNDER_SALT = 0x5AL
    const val MONUMENT_RELIC_SALT = 0x5BL
    const val BURIAL_GOODS_SALT = 0x5CL

    /** A slaying field is smaller than a battle's: one beast and those who stood against it. */
    const val MONSTER_FIELD_RADIUS = 120.0

    /** A seal stands a little way from where the beast raged, small: a crystal on a ring of stone. */
    const val SEAL_OFFSET = 600.0
    const val SEAL_RADIUS = 14.0
    const val SEAL_KEY = 2_000_000L

    const val LAIR_RADIUS = 40.0

    /** Fewest gods any world has, and the smallest world the count shrinks for. */
    const val MIN_DEITIES = 3
    const val MIN_PANTHEON_SCALE = 0.5

    /** A sanctuary stands this far outside its town, small: an idol and its paved floor. */
    const val SANCTUARY_OFFSET = 900.0
    const val SANCTUARY_RADIUS = 28.0

    /** Added to a cult's index for its sanctuary's offset key, so it never shares a key with a tomb's figure. */
    const val SANCTUARY_KEY = 1_000_000L

    /** Hostility left between two peoples once their rulers wed, as a share of what there was. */
    const val MARRIAGE_HOSTILITY_FACTOR = 0.5

    /** How much a culture no civ holds yet counts over distance, when choosing where the second people settles. */
    const val RIVAL_NEW_CULTURE_BONUS = 1.25

    /** Per tier below a city, so the second people prefers a town to a hamlet at a similar distance. */
    const val RIVAL_TIER_PENALTY = 0.25

    /** Share of its people a town loses when its revolt is put down. */
    const val REVOLT_POPULATION_LOSS = 0.2

    /** Endings that leave survivors with somewhere to go. A town that dwindled to nothing leaves nobody. */
    val REFUGEE_CAUSES = setOf(
      EventKind.SETTLEMENT_RAZED, EventKind.SETTLEMENT_BURIED, EventKind.SETTLEMENT_FORSAKEN
    )

    /**
     * How much a borne-out conviction multiplies an Order's weight, at full strength.
     *
     * One, so a people whose century entirely bore out one Order weight it twice. Deliberately larger than
     * `OrderInfluence.favouring`'s default bonus of 0.5, because **what a civilisation lived through should
     * matter more than which Order won the previous world.** The victor tilts a world's history; it does not
     * decide any single people's convictions, and inverting those two would turn the Orders from a thread
     * through a chronicle into a property of the server's configuration.
     */
    const val LEANING_STRENGTH = 1.0

    /**
     * How much of Chaos' and Eternity's leaning is the premise they share rather than the evidence they read
     * differently. See `leaningOf`.
     *
     * Two thirds. High on purpose: the shared half is what makes the two symmetric at year one, which is when
     * most oaths are sworn, and therefore what leaves the world's `OrderInfluence` as the thing that decides
     * them.
     */
    const val PREMISE_SHARE = 2.0 / 3.0

    /**
     * Years an oath is held before it can be reconsidered.
     *
     * Two centuries. Without it a civ can swear and recant inside two ticks, which reads as indecision rather
     * than as the schism the log calls it - and it would make `ORDER_SWORN` and `ORDER_SCHISM` fire in nearly
     * equal numbers, which is not what a world with three ancient Orders in it looks like.
     */
    const val ORDER_OATH_GRACE = 200

    /** Years of nothing happening after which Chaos' promise of an ending starts to look unkept. */
    const val ORDER_PATIENCE = 300

    /**
     * Times one people may change its Order. See `reconsider` for the cascade this bounds.
     *
     * One. Two revisions in a thousand years is a people with no convictions, and - more to the point - an
     * unbounded count drifts every civilisation on a blighted world into Chaos regardless of the world's
     * `OrderInfluence`.
     */
    const val MAX_SCHISMS = 1

    /**
     * Towns given up before a people stops believing Eternity could have held the line.
     *
     * The highest bar of the three, because Eternity's own promise is "any cost". See `contradicted` for the
     * measurement that put it here rather than at one.
     */
    const val ETERNITY_SCHISM_LOSSES = 3

    /** Towns given up before the Circle reads the world as off its hour. The lowest bar: it is the swing vote. */
    const val CIRCLE_SCHISM_LOSSES = 1

    /**
     * How much a technology gate is brought forward for the Order whose work the building is. See `eased`.
     *
     * A tenth, against gates that run from 0.20 to 0.45 - so a sworn people reaches its own kind of site
     * roughly a century earlier than its neighbours over the default thousand-year span, and never reaches one
     * its neighbours cannot.
     */
    const val ORDER_TECHNOLOGY_EASE = 0.10

    /**
     * How much likelier a Chaos-sworn seer is to walk out to a wound and not come back.
     *
     * Doubled, which sounds severe and is bounded by everything around it: the roll still needs a prophet or a
     * scholar, a wound inside `seerRange`, and `SEER_LOSS_CHANCE` (0.18) to begin with. What it buys is that
     * "figures with no known grave" in `chronicle -Pquests` skews towards the Order that sent them, which is a
     * quest hook with a reason attached rather than a scatter.
     */
    const val CHAOS_SEER_ZEAL = 2.0

    /**
     * Extent of a shrine in metres.
     *
     * Small, like every other site marker, and for the reason the invariant checks: a point marker wider than
     * `ChunkMaterializer.MARKER_MARGIN` (320 m) is simply absent from every chunk further away than it and
     * materialises with a dead straight edge down one side. Eighteen metres is a stone circle, which is what
     * this is.
     */
    const val SHRINE_RADIUS = 18.0
  }
}
