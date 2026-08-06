package net.bestia.worldgen.core

import net.bestia.worldgen.vector.Vec2d

/**
 * The world's history, as an append-only log of structured events plus the entities they happened to.
 *
 * ### Why this is a third world-tier product rather than more vector features
 *
 * The architecture document's scale-tier table lists three things computed once and read-only forever:
 * the world raster, the world vector set, and *the history event log*. The first two had homes -
 * [LayerStore] and [FeatureStore] - and this one did not, so until now `World` carried two of the three.
 *
 * It genuinely is a third kind of thing. A raster is addressed by position, a feature by position and
 * kind, and an event by *year and actor*: "what happened to this town", "who held this sword before it
 * was buried", "which war produced this ruin". None of those is a spatial query, and forcing them into
 * the feature store would mean either a marker per event - hundreds of thousands of zero-extent points
 * that no chunk will ever want - or losing the causal links between them, which are the entire product.
 *
 * What *does* go in the feature store is the physical residue: a ruin, a battlefield, a tomb. Those are
 * places, they have extent, and chunk generation has to know about them. The log is the reason they are
 * where they are, and stays here.
 *
 * ### Determinism
 *
 * Every list here is in simulation order, which is a pure function of the seed. Ids are indices into
 * these lists, so they are stable across runs and safe to store in a feature's station channels - which
 * is how a `RUIN` marker points back at the settlement whose destruction produced it.
 */
class Chronicle(
  /** Year the simulation began. Negative years are before the present, which is [presentYear]. */
  val startYear: Int,
  /** The year the world is in when play starts. Everything after this has not happened yet. */
  val presentYear: Int,
  val events: List<HistoryEvent>,
  val civs: List<CivRecord>,
  val figures: List<FigureRecord>,
  val artifacts: List<ArtifactRecord>,
  val sites: List<SiteRecord>,
  /**
   * Per-settlement outcome, indexed by [net.bestia.worldgen.civ.SettlementChannels.INDEX].
   *
   * Dense and index-addressed rather than a map, because every settlement the placement stage put down
   * gets a record - including the ones history never founded and the ones it destroyed. "This site was
   * never settled" is an answer the log has to be able to give.
   */
  val settlements: List<SettlementRecord>,
  /** Events dropped by importance pruning. Reported so a sweep can see the log is not silently truncated. */
  val prunedEvents: Int
) {

  val span: IntRange get() = startYear..presentYear

  /** Events involving one entity, in chronological order. The timeline view of anything. */
  fun eventsOf(actor: Actor): List<HistoryEvent> = events.filter { actor in it.actors }

  fun eventsIn(years: IntRange): List<HistoryEvent> = events.filter { it.year in years }

  /** The [n] most important events. What a chronicle prints when asked for "the history of the world". */
  fun topEvents(n: Int): List<HistoryEvent> = events
    .sortedWith(compareByDescending<HistoryEvent> { it.importance }.thenBy { it.year }.thenBy { it.id })
    .take(n)

  /**
   * An artifact's provenance chain: forged by X, wielded by Y, lost in battle B, now in tomb T.
   *
   * Kept as event ids on the record rather than reconstructed by filtering, because the *order* is the
   * chain and it is the order the events were logged in, not the order a filter happens to produce.
   */
  fun provenanceOf(artifact: Int): List<HistoryEvent> =
    artifacts[artifact].provenance.map { events.first { e -> e.id == it } }

  fun civOf(settlement: Int): CivRecord? =
    settlements[settlement].ownerCiv.takeIf { it >= 0 }?.let { civs[it] }

  /** Whether a settlement stood in a given year: founded by then, and not yet abandoned. */
  fun settlementStood(index: Int, year: Int): Boolean {
    val record = settlements[index]
    if (!record.wasFounded || year < record.foundedYear) return false
    return !record.isRuin || year < record.abandonedYear
  }

  fun sitesOfKind(kind: SiteKind): List<SiteRecord> = sites.filter { it.kind == kind }

  /**
   * Whether the Orders appear in this world's history at all.
   *
   * False for a first incarnation, and the one question every reader of the log should ask before rendering
   * anything about them - a view that says "no civilisation is sworn" on a world where the concept does not
   * exist is telling the player about a mechanic they cannot see.
   */
  val hasOrders: Boolean get() = civs.any { it.sworn != null }

  fun civsSwornTo(faction: Faction): List<CivRecord> = civs.filter { it.sworn == faction }

  /** How many civilisations hold each Order at [presentYear], strongest first. Empty when [hasOrders] is false. */
  fun orderCensus(): List<Pair<Faction, Int>> = civs
    .mapNotNull { it.sworn }
    .groupingBy { it }
    .eachCount()
    .entries
    .sortedWith(compareByDescending<Map.Entry<Faction, Int>> { it.value }.thenBy { it.key.ordinal })
    .map { it.key to it.value }

  /** Counts by event kind, for a census line that says what sort of history this world had. */
  fun eventCensus(): List<Pair<EventKind, Int>> = events
    .groupingBy { it.kind }
    .eachCount()
    .entries
    .sortedByDescending { it.value }
    .map { it.key to it.value }

  override fun toString() =
    "Chronicle[$startYear..$presentYear, ${events.size} events, ${civs.size} civs, " +
        "${artifacts.size} artifacts, ${sites.size} sites]"

  companion object {
    /** A chronicle for a world whose history was not simulated. Lets tooling avoid a null check per use. */
    val EMPTY = Chronicle(0, 0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0)
  }
}

/** What sort of entity an [Actor] refers to. */
enum class ActorType { CIV, SETTLEMENT, FIGURE, ARTIFACT, SITE }

/**
 * One of the three Orders a people can be sworn to: what they believe the mana means.
 *
 * ### Why this is here and not in `history/`
 *
 * [CivRecord] holds one, and `core` does not import a stage package - which is exactly why
 * `CivRecord.cultureIndex` is an `Int` rather than a `net.bestia.worldgen.civ.Culture`. An Order is chronicle
 * vocabulary in the same way [FigureRole] and [ArtifactKind] are, so it lives beside them and the history
 * stage reads it from here. What *is* in `history/` is `OrderInfluence`, which is tuning rather than vocabulary.
 *
 * ### Why this is not a [net.bestia.worldgen.civ.Culture]
 *
 * A culture is *how a people lives* - what ground it settles, how it builds, what it trades - and there are
 * four of them. An Order is *what it believes about the mana*, and there are three. The two are orthogonal: a
 * highland people can be sworn to any of these, and folding them together would both cost a culture and make
 * "mining people" and "thinks the world should burn" the same axis.
 *
 * ### The Orders are absent from most worlds' history
 *
 * They appear only when a previous incarnation had a victor - see `OrderInfluence`. The first world has no
 * Order in its chronicle at all, because no Order has yet won anything to be remembered for.
 *
 * Folded into the history stage's `paramsVersion` by name via [catalogueDigest], so [label] is a tuning
 * change like any other: it reaches every rendered chronicle line.
 */
enum class Faction(
  /** How the chronicle names it. */
  val label: String,
  /** The short form, for a table or a census line. */
  val shortForm: String
) {
  /** The world is already dying; hasten it, so that the next one can be born. */
  CHAOS("the Order of Chaos", "Chaos"),

  /** This world can be held, and holding it is worth any cost. */
  ETERNITY("the Order of Eternity", "Eternity"),

  /** The cycle is not to be stopped, only kept to its proper hour. */
  CIRCLE("the Order of the Circle", "Circle");

  /** The Order whose conviction is the flat contradiction of this one. The Circle opposes neither. */
  val opposite: Faction?
    get() = when (this) {
      CHAOS -> ETERNITY
      ETERNITY -> CHAOS
      CIRCLE -> null
    }

  companion object {

    /**
     * Fingerprint of the roster, by name - so a reorder is free.
     *
     * Nothing stores an `Order` ordinal *except* the `order` station channel, and that is written and read
     * inside one generation of one world, never across two. [label] is folded because it reaches prose.
     */
    fun catalogueDigest(): Long {
      val digest = ParamsDigest()
      for (order in entries) digest.put(order.name, order.label)
      return digest.value
    }
  }
}

/**
 * A typed reference into one of the chronicle's entity lists.
 *
 * Typed rather than a bare int, because the whole value of the log is that "who did this" can be
 * answered - and an untyped index into one of five lists is an invitation to answer it wrongly.
 */
data class Actor(val type: ActorType, val index: Int) {
  override fun toString() = "${type.name.lowercase()}#$index"
}

/**
 * What happened.
 *
 * Named at the granularity a player would recognise, because these names end up in the chronicle a
 * developer reads and, later, in whatever surfaces history in the game.
 */
enum class EventKind(
  /**
   * Importance floor, 0 to 100. The pruner keeps everything above a threshold and samples below it, so
   * this is what decides whether a kind survives a thousand-year log at all.
   *
   * A founding is remembered forever; a good harvest is not.
   */
  val baseImportance: Int
) {
  CIV_FOUNDED(90),
  SETTLEMENT_FOUNDED(60),
  SETTLEMENT_WALLED(45),
  WAR_DECLARED(70),
  BATTLE(55),
  SIEGE(60),
  SETTLEMENT_SACKED(65),
  SETTLEMENT_RAZED(80),
  SETTLEMENT_ABANDONED(50),
  CONQUEST(70),
  PEACE(40),
  PLAGUE(55),
  FAMINE(45),
  FLOOD(35),
  ERUPTION(60),
  FIGURE_ROSE(45),
  FIGURE_DIED(30),
  FIGURE_SLAIN(55),
  ARTIFACT_FORGED(65),
  ARTIFACT_TAKEN(50),
  ARTIFACT_LOST(60),
  ARTIFACT_ENTOMBED(55),
  MONUMENT_BUILT(40),
  TECHNOLOGY(35),
  CIV_FELL(85),

  // The four built sites. Importance is never overridden per event - it comes only from this constant - so
  // these numbers *are* whether the site survives pruning and whether it shows up in `topEvents`. Chosen
  // against the existing scale rather than picked: a mine opening changes a region's economy for centuries and
  // sits near SETTLEMENT_FOUNDED, while a monastery founding and a fort are quieter and longer-lived, at
  // SETTLEMENT_WALLED's level.
  //
  // **All four sit at or above `HistoryParams.importanceFloor`, which is 40, and that is the whole point.**
  // LIGHTHOUSE_LIT was 30 first, on the reasoning that infrastructure is the least memorable of the four - and
  // a test caught what that actually means: below the floor an event is only *sampled*, one in twenty-four, so
  // all four of the reference world's lighthouses existed on the map with nothing in the chronicle to say who
  // lit them or when. A founding that leaves a permanent structure must not be sampled away; MONUMENT_BUILT
  // sits exactly at the floor for the same reason.
  MINE_OPENED(55),
  MONASTERY_FOUNDED(45),
  FORT_BUILT(45),
  LIGHTHOUSE_LIT(40),

  // --- What the mana did ------------------------------------------------------------------------------
  //
  // These five are the history half of the mana subsystem, and they exist so that the corruption on the map has
  // a reason a player can be told. A blighted province the chronicle says nothing about is scenery; one that
  // took a town's fields in the year 340 and emptied it by 480 is somewhere to go and look.
  //
  // All five are at or above `HistoryParams.importanceFloor` (40) for the reason recorded above: below the
  // floor an event is sampled one in twenty-four, and a `WOUND` on the ground with nothing in the log saying
  // what happened there is the lighthouse mistake again.

  /**
   * The first cause: something came down, and the mana pooled where it landed.
   *
   * Once per world, early, at the highest-mana place on land - which is to say the log explains the field
   * rather than describing it. Every other event here cites this one, so `provenanceOf` and the causal
   * closure in `HistorySim.prune` thread a blighted town back to it in one hop.
   */
  STAR_FELL(70),

  /** The corruption reached a settlement's fields. Costs population and wealth; shaped on [PLAGUE]. */
  BLIGHT_SPREAD(50),

  /**
   * Wards raised against the blight, which is why a town still stands in a high-mana province at all.
   *
   * Shaped on [SETTLEMENT_WALLED], and the same argument: the interesting thing about a defence is that it
   * dates from the second time somebody needed it.
   */
  WARD_RAISED(45),

  /**
   * Emptied by the blight rather than by war, plague or ash.
   *
   * Goes through the same `abandon` path as every other ending, so it writes `ruinCause` and leaves a `RUIN`
   * site - a ruin in corrupted land explains itself with no new machinery.
   */
  SETTLEMENT_FORSAKEN(80),

  /**
   * Emptied by an eruption's ashfall.
   *
   * Separate from [ERUPTION], which is the mountain waking. Both used to be [ERUPTION] because the eruption *was*
   * the obituary - there was no event for a volcano, only for the town it killed - and the reuse survived the pass
   * that gave volcanoes their own event, leaving a chronicle in which "eruption" meant two different things and a
   * census that counted them together.
   *
   * Named `SETTLEMENT_*` because that is what every other ending is called: `abandon` is reached with
   * [SETTLEMENT_ABANDONED], [SETTLEMENT_RAZED] and [SETTLEMENT_FORSAKEN], and the eruption path was the one caller
   * passing a kind that was not about a settlement at all.
   *
   * The burial **cites** the eruption, so `Chronicle.provenanceOf` threads an ash ruin back to the mountain that
   * made it - which is the whole of what "an eruption is geography" buys.
   */
  SETTLEMENT_BURIED(80),

  /** A prophet or a scholar went out to the wound and did not come back. */
  SEER_VANISHED(45),

  /**
   * A figure crossed the deep waste and died in it, and was buried where they fell.
   *
   * [SEER_VANISHED]'s sibling, and the difference between them is the whole reason both exist: a seer walks out
   * to a wound *deliberately* and leaves no grave, while this is somebody who was going somewhere else and did
   * not arrive - so there is a barrow, and it is out in the sand rather than beside their home town. Same
   * importance, because to a chronicle both read as "a named person was lost".
   */
  TRAVELLER_LOST(45),

  // --- The Orders -------------------------------------------------------------------------------------
  //
  // These four exist only on a world whose `OrderInfluence` is present, which is to say one whose predecessor
  // had a victor. On the first world they are never logged and `Order` is never named, which is deliberate:
  // there is nothing for a chronicle to remember an Order *for* until one has won something.
  //
  // All four are at or above `HistoryParams.importanceFloor` (40), on the argument the mana kinds record
  // above: below the floor an event is sampled one in twenty-four, so a shrine standing on the ground with
  // nothing in the log to say who raised it would be the `LIGHTHOUSE_LIT` mistake a third time.

  /**
   * A civilisation swore itself to one of the three Orders.
   *
   * Cites the civ's [CIV_FOUNDED] - and [STAR_FELL] too, once that has happened - so `Chronicle.provenanceOf`
   * threads a sworn people back to the thing they are arguing about in one hop.
   */
  ORDER_SWORN(60),

  /**
   * A civilisation abandoned its Order for another.
   *
   * Rare, and it is what keeps a world from coming out as three monoliths - as well as the sharpest thing an
   * NPC can be bitter about, since the schism is in the log with the year and the reason on it.
   */
  ORDER_SCHISM(55),

  /** An Order's shrine went up: a cairn at a wound, a ward stone on a frontier, a circle on high ground. */
  SHRINE_RAISED(45),

  /**
   * An Order's signature working, performed somewhere it mattered.
   *
   * The quietest of the four and the one that does the "a hint here and there" work: it leaves nothing on the
   * ground, it happens repeatedly across a thousand years, and it is what a townsperson brings up unprompted.
   */
  RITE_PERFORMED(45);

  companion object {

    /**
     * Fingerprint of the importance table.
     *
     * These numbers decide what a thousand-year log *contains*: everything at or above `HistoryParams
     * .importanceFloor` is kept and the rest is sampled, so lowering one kind by ten points can delete a whole
     * class of event from every world's history. That is a tuning change like any other, and it belongs in the
     * history stage's version.
     *
     * By name, so a reorder is free - nothing stores an `EventKind` ordinal.
     */
    fun catalogueDigest(): Long {
      val digest = ParamsDigest()
      for (kind in entries) digest.put(kind.name, kind.baseImportance)
      return digest.value
    }
  }
}

/**
 * One logged event.
 *
 * [causes] makes the log a causal graph rather than a list, and that is what makes quest mining
 * possible: "a stolen artifact never recovered" is an `ARTIFACT_TAKEN` with no later `ARTIFACT_LOST`
 * or `ARTIFACT_ENTOMBED` naming it as a cause.
 */
data class HistoryEvent(
  val id: Int,
  val year: Int,
  val kind: EventKind,
  /** Everyone and everything involved. First actor is the subject by convention. */
  val actors: List<Actor>,
  /** Where it happened, when that is meaningful. Null for a technology change or a peace treaty. */
  val where: Vec2d?,
  /** Event ids this one followed from. Empty for a first cause. */
  val causes: List<Int>,
  val importance: Int,
  /**
   * One rendered line.
   *
   * Deliberately stored rather than reconstructed. A log is only useful if you can read it, and a
   * renderer that re-derives the sentence from actor indices has to keep a second copy of every name,
   * role and relationship the simulation knew - which is how a chronicle ends up saying
   * "figure#12 did something to settlement#7".
   */
  val detail: String
) {
  override fun toString() = "$year: $detail"
}

/** Cultural and demographic outcome of one settlement site over the whole simulated span. */
data class SettlementRecord(
  val index: Int,
  /** 0 when history never founded this site at all - it was placed, and nobody came. */
  val foundedYear: Int,
  /** Year it emptied, or 0 while it still stands. */
  val abandonedYear: Int,
  val population: Int,
  /** 0 to 1. Drives building quality, business mix and how much of the town is stone. */
  val wealth: Double,
  /** Civ that owns it now, or -1 for a ruin nobody holds. */
  val ownerCiv: Int,
  val foundingCiv: Int,
  val timesSacked: Int,
  /** Year the walls went up, or 0 for an unwalled place. */
  val wallYear: Int,
  /** Population when the walls went up, which is the extent they were built to enclose. */
  val wallPopulation: Int,
  val nameSeed: Long,
  /** Name before the last conquest, or 0. NPCs of the old culture still use it. */
  val oldNameSeed: Long,
  /** What emptied it, for a ruin. */
  val ruinCause: EventKind?,
  /** Sites - tombs, battlefields, monuments - that belong to this settlement. */
  val sites: List<Int>
) {
  val wasFounded get() = foundedYear != 0
  val isRuin get() = abandonedYear != 0

  /** How threatened this place has been. Drives whether it walls itself and how heavily. */
  val threat: Double get() = (timesSacked * 0.4).coerceAtMost(1.0)
}

data class CivRecord(
  val index: Int,
  val cultureIndex: Int,
  val nameSeed: Long,
  val foundedYear: Int,
  /** Year the last of its settlements fell, or 0 if it still exists. */
  val endedYear: Int,
  val capital: Int,
  /** 0 to 1. Raises carrying capacity and unlocks the specialist trades. */
  val technology: Double,
  val peakPopulation: Int,
  val settlements: List<Int>,
  /**
   * Specific logged wrongs, as `(other civ, event id)`.
   *
   * A grudge that points at an event is one an NPC can cite. A scalar hostility number is one that can
   * only be complained about in the abstract, which is the difference between a world with history and a
   * world with a diplomacy slider.
   */
  val grudges: List<Pair<Int, Int>>,

  /**
   * The Order this people is sworn to at [Chronicle.presentYear], or null for an unaligned one.
   *
   * Null on every civ of a world whose predecessor had no victor, and null on a good share of the civs even
   * where the Orders *are* present - `HistoryParams.orderSwornShare` decides how many ever swear at all. An
   * unaligned people is the normal case rather than an oversight, and it is most of what keeps the Orders a
   * thread through a history rather than the subject of one.
   *
   * The *current* Order, after any schism. What it was before, and when it changed, is in the log as
   * [EventKind.ORDER_SWORN] and [EventKind.ORDER_SCHISM] - which is the same choice
   * [SettlementRecord.oldNameSeed] makes for a conquered name, and for the same reason: a record holds the
   * present, and the log holds how it got there.
   */
  val sworn: Faction? = null,

  /** Year it swore to [sworn] - its latest oath, not its first - or 0 if it never swore. */
  val swornYear: Int = 0
) {
  val exists get() = endedYear == 0
}

/** What a notable figure was known for. */
enum class FigureRole { RULER, GENERAL, PROPHET, SMITH, EXPLORER, SCHOLAR }

data class FigureRecord(
  val index: Int,
  val nameSeed: Long,
  val role: FigureRole,
  val civ: Int,
  val homeSettlement: Int,
  val birthYear: Int,
  /** 0 while still alive at [Chronicle.presentYear]. */
  val deathYear: Int,
  /** Site index of the tomb, or -1 for a figure with no known grave. */
  val restingSite: Int,

  /**
   * The Order this person held to, or null.
   *
   * Usually their civ's, and deliberately *not* always: a person who holds a different Order from the people
   * around them is where [EventKind.ORDER_SCHISM] comes from, and a prophet who died for a conviction their
   * own city did not share is a better grave to find than one who agreed with everybody.
   */
  val sworn: Faction? = null
)

/** What sort of thing an artifact is. Decides what it is made of and who wanted it. */
enum class ArtifactKind { BLADE, CROWN, RELIQUARY, TOME, RING, BANNER }

data class ArtifactRecord(
  val index: Int,
  val nameSeed: Long,
  val kind: ArtifactKind,
  val forgedYear: Int,
  val forgedBy: Int,
  val material: String,
  /**
   * Name seed of the settlement it was made in, or 0.
   *
   * Stored so that every view renders the same name. The alternative - passing the forge town's name in at
   * render time - meant a caller that did not know where it was made produced a different name for the same
   * sword, which is how the chronicle tool and the simulation came to disagree.
   */
  val forgedAtNameSeed: Long,
  /**
   * Where it is now: a site index, or -1 if it was never lost and is still in use.
   *
   * The point of tracking it. An artifact whose chain ends in a tomb is one a player can go and find,
   * and the tomb is a real place in the world because [SiteRecord] put it there.
   */
  val restingSite: Int,
  val provenance: List<Int>
)

/**
 * The kinds of place history leaves on the ground.
 *
 * Appended to rather than reordered, on the same argument as [net.bestia.worldgen.bio.Biome]: a site's kind
 * reaches the feature store as a [net.bestia.worldgen.vector.FeatureKind] rather than as an ordinal, but
 * `SiteRecord` is part of the chronicle and the chronicle is a world-tier product.
 *
 * The first four are *residue* - what is left after something happened. The last four are **built on purpose**,
 * which is the difference worth noticing: a ruin is where a town was, and a fort is where somebody decided a
 * fort should be. That is why they are gated on a civ having the reach and the reason to build them.
 */
enum class SiteKind {
  RUIN,

  /**
   * A town under a volcano's ash: residue, like a [RUIN], but a mound rather than a scatter of walls.
   *
   * The one site kind an eruption produces, and a separate kind rather than a `cause` channel on [RUIN] for
   * `SiteChannels.RESOURCE`'s reason stated one level up - a site's kind *is* its `FeatureKind`, so four kinds
   * cost nothing extra while one kind plus a type channel would cost a channel on every site marker in the world.
   *
   * The reason is **not** that the runtime could not otherwise tell the two apart: it can, through
   * `SettlementRecord.ruinCause`. The reason is that the *materialiser* has to build a mound instead of a ruin
   * field, and it reads features rather than the chronicle.
   *
   * Pompeii is the reference, and the scale is why this is a site rather than a landform: a `VOLCANO` or a
   * `CALDERA` would be 5-20 km across, two orders past the `ChunkMaterializer.MARKER_MARGIN` a site radius is
   * capped under. Those belong to `VolcanismStage`'s vent features. An ash ruin is settlement-sized, causally
   * tied to an eruption in the log, and the one part of it a player can walk into.
   */
  ASH_RUIN,

  BATTLEFIELD,
  TOMB,
  MONUMENT,

  /** A working or worked-out mine at an ore deposit, with a settlement near enough to have opened it. */
  MINE,

  /** A religious house, deliberately somewhere poor and hard to reach. */
  MONASTERY,

  /** A frontier post on high or narrow ground between two civilisations. */
  FORT,

  /** A light on a headland, kept by the port whose approaches it guards. */
  LIGHTHOUSE,

  /**
   * Valuables carried into a cave and never carried out.
   *
   * Residue, like a ruin, and for a sharper reason than the others: **nearly every hoard ever dug up was
   * buried by somebody who meant to come back for it.** So this is not treasure that was placed - it is
   * treasure whose owner was killed, and the event that killed them is in the log with it. That is what makes
   * it a quest hook rather than a loot table: the chronicle can say whose it was, what they were running from,
   * and in which year.
   *
   * The only site kind that is underground, which is why [SiteRecord] carries an elevation.
   */
  HOARD,

  /**
   * Where the mana came into the world: the place [EventKind.STAR_FELL] names.
   *
   * Residue, like a ruin, and the one kind that is neither built nor left by people - which is exactly why it
   * earns a kind of its own rather than being a `MONUMENT` with a different colour. A `SiteRecord` is the
   * chronicle's only mechanism for putting a *place* on the map, and the thing a world's history keeps
   * referring to has to be somewhere a player can walk to.
   *
   * At most three per world, at the peaks of the mana field, so this is the rarest kind by a wide margin.
   */
  WOUND,

  /**
   * A place one of the three Orders raised to work at: a crystal cairn, a ward stone, a gnomon circle.
   *
   * Built on purpose, like a [FORT] - and the one kind whose *shape* is decided by something other than its
   * kind. Which of the three it is comes from `SiteChannels.ORDER` on the marker, not from a kind of its own,
   * and that is a deliberate departure from the argument [ASH_RUIN] makes one screen up.
   *
   * That argument was that a kind plus a type channel costs a channel on every site marker in the world, while
   * four kinds cost nothing extra. It is weaker here for two reasons. The channel is going onto every marker
   * regardless, because every view that renders a site's prose wants to know whether an Order raised it. And
   * the three shrines are the same *class* of structure - a small stone thing standing on graded ground,
   * tens of metres across - where a mound and a ruin field genuinely are not.
   *
   * If they ever diverge structurally, splitting this into three kinds is mechanical: the channel already
   * carries the discriminator the split would need.
   */
  SHRINE
}

data class SiteRecord(
  val index: Int,
  val kind: SiteKind,
  val position: Vec2d,
  /** Year it came to be - razed, fought over, buried, raised. */
  val year: Int,
  /** Settlement it belongs to or replaced, or -1. */
  val settlement: Int,
  val civ: Int,
  /** Extent in metres. A razed city is a large ruin field; a tomb is a doorway. */
  val radius: Double,
  /** 0 = fresh, 1 = nothing left but earthworks. Derived from age at [Chronicle.presentYear]. */
  val decay: Double,
  val nameSeed: Long,
  /** Artifact resting here, or -1. */
  val artifact: Int,
  /** Figure buried here, or -1. */
  val figure: Int,
  /**
   * [net.bestia.worldgen.resource.ResourceType] ordinal for a [SiteKind.MINE], -1 for every other kind.
   *
   * On the record rather than derived from position because "what does this mine produce" is a question about
   * the *history* - a worked-out mine is still a silver mine - and rediscovering it would mean a spatial query
   * against the deposit index from a class that has no access to one.
   */
  val resource: Int = -1,

  /**
   * Elevation in metres for a site that is not on the ground, or [Double.NaN] for one that is.
   *
   * NaN rather than the terrain height, deliberately: "this is at ground level, whatever that turns out to be"
   * and "this is at 412 m" are different statements, and only the second one should survive a change to the
   * heightfield. Every kind but [SiteKind.HOARD] is NaN today.
   */
  val elevation: Double = Double.NaN,

  /**
   * The Order that raised this, or null for every kind but a [SiteKind.SHRINE].
   *
   * The discriminator [SiteKind.SHRINE]'s KDoc argues for: it decides which of the three structures the
   * materialiser builds, and which of three name forms the site gets, so one kind covers all three.
   *
   * Null rather than absent on the other kinds, and not because nothing else could ever have one - a monument
   * raised by a sworn people is a real thing this could describe later. It is null today because nothing sets
   * it, and a reader must treat null as "no Order raised this" rather than as "unknown".
   */
  val faction: Faction? = null
)
