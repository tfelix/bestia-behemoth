package net.bestia.zone.townsfolk.knowledge

import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.MonsterFate
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.geometry.Compass
import net.bestia.zone.world.SettlementLoreService
import kotlin.math.hypot

/**
 * Old sayings about the places the chronicle hid: a hoard, a sleeping world boss, the field one fell
 * on, a relic lost in battle. And about the Empyrite Seeds, asleep or awake, so a legend may lead a guild to one.
 *
 * A saying points the way from the nearest standing town. Each town tells it its own way: most tell it
 * true, some have the direction and the age wrong, and some point where nothing lies. A player who asks
 * in several towns can work out which is which - that is the game.
 */
object LegendKnowledge {

  /** Topics of legends, clear of chronicle event ids below and of rumours above. */
  const val TOPIC_BASE = 30_000

  const val SLOT_DIRECTION = "direction"
  const val SLOT_SEED = "seed"

  enum class Truth { TRUE, DISTORTED, MYTH }

  /** One legend as one town tells it. [anchor] and [heading] are where it sends a listener. */
  class Told(
    val subject: Subject,
    val truth: Truth,
    /** The settlement the saying is told from. */
    val anchor: Int,
    /** A [Compass] octant. */
    val heading: Int,
    val knowledge: Knowledge,
  )

  /** A hidden place a legend can be about. */
  class Subject(
    val index: Int,
    val kind: LegendKind,
    val position: Vec2d,
    val year: Int,
    val slots: Map<String, Knowledge.Slot>,
    /** The artifact a [LegendKind.LOST_RELIC] legend is about, or -1. */
    val artifact: Int = -1,
  )

  /**
   * The legends one town tells, nearest first.
   *
   * @param variantsOf how many phrasings each kind has
   */
  fun toldAt(
    chronicle: Chronicle,
    settlement: Int,
    worldSeed: Long,
    positions: Map<Int, Vec2d>,
    variantsOf: (LegendKind) -> Int = { 1 },
    seeds: List<EmpyriteSeed> = emptyList(),
  ): List<Told> {
    val here = positions[settlement] ?: return emptyList()
    val standing = positions.filterKeys { chronicle.settlementStood(it, chronicle.presentYear) }
    if (standing.isEmpty()) return emptyList()

    return subjectsOf(chronicle, seeds)
      .filter { hypot(it.position.x - here.x, it.position.y - here.y) <= rangeOf(it.kind) }
      .sortedBy { hypot(it.position.x - here.x, it.position.y - here.y) }
      .take(PER_TOWN)
      .map { tell(chronicle, it, settlement, worldSeed, standing, variantsOf(it.kind)) }
  }

  /** Every hidden place in the world, in a fixed order so a subject's index is its topic. The seeds come last. */
  fun subjectsOf(chronicle: Chronicle, seeds: List<EmpyriteSeed> = emptyList()): List<Subject> {
    val found = ArrayList<Subject>()

    fun add(kind: LegendKind, site: Int, year: Int, slots: Map<String, Knowledge.Slot>, artifact: Int = -1) {
      val position = chronicle.sites.getOrNull(site)?.position ?: return
      found.add(Subject(found.size, kind, position, year, slots, artifact))
    }

    for (site in chronicle.sitesOfKind(SiteKind.HOARD)) {
      add(LegendKind.HOARD, site.index, site.year, emptyMap())
    }

    for (monster in chronicle.monsters) {
      val kind = when (monster.fate) {
        MonsterFate.SLAIN -> LegendKind.SLAYING
        MonsterFate.SEALED -> LegendKind.SEAL
        MonsterFate.WITHDRAWN, MonsterFate.VANISHED -> LegendKind.LAIR
      }
      val name = named(ChronicleNames.monsterOf(chronicle, monster.index), HistoryKnowledge.UNKNOWN_MONSTER)
      val slots = mapOf(HistoryKnowledge.SLOT_MONSTER to name)
      add(kind, monster.site, monster.endedYear, slots)
    }

    for (relic in chronicle.artifacts) {
      if (!relic.unclaimed || chronicle.sites.getOrNull(relic.restingSite)?.kind != SiteKind.BATTLEFIELD) continue
      val lostYear = chronicle.provenanceOf(relic.index).lastOrNull()?.year ?: relic.forgedYear
      val name = named(ChronicleNames.artifactOf(chronicle, relic.index), HistoryKnowledge.UNKNOWN_ARTIFACT)
      val slots = mapOf(HistoryKnowledge.SLOT_ARTIFACT to name)
      add(LegendKind.LOST_RELIC, relic.restingSite, lostYear, slots, artifact = relic.index)
    }

    for (seed in seeds) {
      val patron = SeedPatrons.of(chronicle, seed)
      val slots = mapOf(SLOT_SEED to Knowledge.Slot.Name(seed.name), patron.slot)
      found.add(Subject(found.size, patron.kind, Vec2d(seed.x, seed.y), patron.year, slots))
    }

    return found
  }

  private fun tell(
    chronicle: Chronicle,
    subject: Subject,
    teller: Int,
    worldSeed: Long,
    standing: Map<Int, Vec2d>,
    variants: Int,
  ): Told {
    val (anchor, anchorAt) = standing.minBy { (_, at) -> hypot(subject.position.x - at.x, subject.position.y - at.y) }
    val trueHeading = Compass.octantOf(subject.position.x - anchorAt.x, subject.position.y - anchorAt.y)
    val truth = truthOf(GenRng.hashUnit(worldSeed, subject.index.toLong(), teller.toLong(), TRUTH_SALT))
    val twist = GenRng.hashUnit(worldSeed, subject.index.toLong(), teller.toLong(), TWIST_SALT)

    // A twisted tale is off by one point of the compass; a myth points well away from anything real.
    val heading = when (truth) {
      Truth.TRUE -> trueHeading
      Truth.DISTORTED -> trueHeading + if (twist < 0.5) -1 else 1
      Truth.MYTH -> trueHeading + 3 + (twist * 3).toInt().coerceAtMost(2)
    }.mod(Compass.POINTS)

    val era = Era.of(chronicle.presentYear - subject.year).let { if (truth == Truth.DISTORTED) misdated(it) else it }

    val slots = LinkedHashMap(subject.slots)
    slots[SLOT_DIRECTION] = Knowledge.Slot.Token(Compass.tokenOf(heading))
    slots[HistoryKnowledge.SLOT_PLACE] = named(ChronicleNames.placeOf(chronicle, anchor), HistoryKnowledge.UNKNOWN_PLACE)
    slots[HistoryKnowledge.SLOT_ERA] = Knowledge.Slot.Token(era.key)

    val knowledge = Knowledge(
      topic = TOPIC_BASE + subject.index,
      key = subject.kind.key,
      slots = slots,
      importance = IMPORTANCE,
      year = subject.year,
      locality = Locality.NEARBY,
      variants = variants,
      certainty = Certainty.LEGEND,
    )

    return Told(subject, truth, anchor, heading, knowledge)
  }

  /** A tale that drifted is told one age older, or younger once it is as old as it gets. */
  private fun misdated(era: Era): Era {
    return if (era == Era.ANCIENT) Era.DISTANT else Era.entries[era.ordinal + 1]
  }

  private fun truthOf(roll: Double): Truth {
    return when {
      roll < TRUE_SHARE -> Truth.TRUE
      roll < TRUE_SHARE + DISTORTED_SHARE -> Truth.DISTORTED
      else -> Truth.MYTH
    }
  }

  /** A world boss or a golden stone is talked about much further away than a lost purse. */
  private fun rangeOf(kind: LegendKind): Double {
    return when (kind) {
      LegendKind.LAIR, LegendKind.SEAL, LegendKind.SLAYING -> SettlementLoreService.NEARBY_RANGE * 2
      LegendKind.EMPYRITE_GOD, LegendKind.EMPYRITE_CULT -> SettlementLoreService.NEARBY_RANGE * 2
      else -> SettlementLoreService.NEARBY_RANGE
    }
  }

  private fun named(name: String?, unknownKey: String): Knowledge.Slot {
    return if (name == null) Knowledge.Slot.Token(unknownKey) else Knowledge.Slot.Name(name)
  }

  private const val TRUE_SHARE = 0.6
  private const val DISTORTED_SHARE = 0.3

  /** Few enough that a town is not a treasure map. */
  private const val PER_TOWN = 4

  /** Just below famous: the few who keep a legend bring it up before their town's lesser news. */
  private const val IMPORTANCE = 65

  private const val TRUTH_SALT = 0x4C47L
  private const val TWIST_SALT = 0x4C48L
}
