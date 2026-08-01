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
  SETTLEMENT_GREW(10),
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
  FIGURE_BORN(15),
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
  LIGHTHOUSE_LIT(40);

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
  val grudges: List<Pair<Int, Int>>
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
  val restingSite: Int
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

/** A place history left behind. These become vector features, one marker each. */
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
  LIGHTHOUSE
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
  val resource: Int = -1
)
