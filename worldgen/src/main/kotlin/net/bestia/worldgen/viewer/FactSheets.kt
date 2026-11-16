package net.bestia.worldgen.viewer

import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.Faction
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.FigureRole
import net.bestia.worldgen.core.HistoryEvent
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.SiteRecord
import net.bestia.worldgen.history.HistoryChannels
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker

/**
 * Fact sheets for the LLM prose spike: one JSON object per entity a text can be written about.
 *
 * A sheet is the only thing a model sees, so a text can only name what is in it. The validator checks every
 * name and year of a text against the sheet's `names` and `years`, and also against every word in its event
 * lines, because an event line is rendered by the simulation and names places the record fields do not.
 *
 * Spike code: maps instead of types, and names rendered here rather than shared with `HistorySim`.
 */
class FactSheets(private val chronicle: Chronicle, features: FeatureStore) {

  /**
   * The culture each settlement's name is rendered in. `HistorySim` names a town in the culture of the ground
   * it stands on, not of its owner, and only the history marker keeps that culture.
   */
  private val siteCulture: Map<Int, Int> = features.all()
    .filter { it.kind == FeatureKind.SETTLEMENT_HISTORY }
    .filterIsInstance<PointMarker>()
    .associate { it.attribute(HistoryChannels.INDEX).toInt() to it.attribute(HistoryChannels.CULTURE).toInt() }

  private val eventsById = chronicle.events.associateBy { it.id }

  fun all(): List<Map<String, Any?>> =
    chronicle.civs.indices.map(::civ) +
        chronicle.figures.indices.map(::figure) +
        chronicle.dynasties.indices.map(::dynasty) +
        chronicle.artifacts.indices.map(::artifact) +
        wars() +
        chronicle.sitesOfKind(SiteKind.BATTLEFIELD).map(::battle) +
        chronicle.deities.indices.map(::deity) +
        chronicle.cults.indices.map(::cult) +
        chronicle.monsters.indices.map(::monster)

  /** How many texts one world needs, by text kind. The basis for the wall-clock estimate. */
  fun textCounts(): Map<String, Int> {
    val wars = chronicle.events.count { it.kind == EventKind.WAR_DECLARED }
    return linkedMapOf(
      "epitaph (one per tomb with a known dead)" to chronicle.sitesOfKind(SiteKind.TOMB).count { it.figure >= 0 },
      "artifact legend (one per artifact)" to chronicle.artifacts.size,
      "hymn (one per deity)" to chronicle.deities.size,
      "war chronicle (two per war, one per side)" to 2 * wars,
      "people chronicle (one per civ)" to chronicle.civs.size,
      "battlefield memorial (one per field)" to chronicle.sitesOfKind(SiteKind.BATTLEFIELD).size,
      "cult tract (one per cult)" to chronicle.cults.size,
      "monster legend (one per world boss)" to chronicle.monsters.size,
      "house chronicle (one per dynasty)" to chronicle.dynasties.size
    )
  }

  // --- One sheet per entity kind --------------------------------------------------------------------

  private fun civ(index: Int): Map<String, Any?> {
    val civ = chronicle.civs[index]
    val actor = Actor(ActorType.CIV, index)
    return sheet(
      actor, "civ", civName(index),
      "culture" to cultureName(civ.cultureIndex),
      "founded" to civ.foundedYear,
      "ended" to civ.endedYear.takeIf { it != 0 },
      "origin" to civ.origin.name.lowercase(),
      "parent" to chronicle.civs.getOrNull(civ.parent)?.let { civName(civ.parent) },
      "capital" to placeName(civ.capital),
      "settlements" to civ.settlements.map(::placeName),
      "patronGod" to chronicle.deities.getOrNull(civ.patron)?.let { deityName(civ.patron, civ.cultureIndex) },
      "order" to civ.sworn?.label,
      "houses" to chronicle.dynasties.filter { it.civ == index }.map { houseName(it.index) },
      "grudges" to civ.grudges.distinctBy { it.first }.map { (other, eventId) ->
        mapOf("against" to civName(other), "event" to eventsById[eventId]?.let(::eventJson))
      },
      events = chronicle.eventsOf(actor).filter { it.importance >= CIV_EVENT_FLOOR }
    )
  }

  private fun figure(index: Int): Map<String, Any?> {
    val figure = chronicle.figures[index]
    val actor = Actor(ActorType.FIGURE, index)
    val grave = chronicle.sites.getOrNull(figure.restingSite)
    return sheet(
      actor, "figure", figureName(index),
      "role" to figure.role.name.lowercase(),
      "people" to civName(figure.civ),
      "home" to placeName(figure.homeSettlement),
      "born" to figure.birthYear,
      "died" to figure.deathYear.takeIf { it != 0 },
      "house" to chronicle.dynasties.getOrNull(figure.dynasty)?.let { houseName(figure.dynasty) },
      "parent" to chronicle.figures.getOrNull(figure.parent)?.let { figureName(figure.parent) },
      "spouse" to chronicle.figures.getOrNull(figure.spouse)?.let { figureName(figure.spouse) },
      "order" to figure.sworn?.label,
      "grave" to grave?.let { mapOf("name" to siteName(it), "year" to it.year) },
      "artifactsMade" to chronicle.artifacts.filter { it.forgedBy == index }.map { artifactName(it.index) },
      "ties" to chronicle.relations.filter { it.from == index || it.to == index }.map {
        val other = if (it.from == index) it.to else it.from
        mapOf("kind" to it.kind.name.lowercase(), "with" to figureName(other), "event" to eventsById[it.since]?.let(::eventJson))
      },
      events = chronicle.eventsOf(actor)
    )
  }

  private fun dynasty(index: Int): Map<String, Any?> {
    val house = chronicle.dynasties[index]
    val members = chronicle.figures.filter { it.dynasty == index }
    return sheet(
      null, "dynasty", houseName(index),
      "ref" to "dynasty#$index",
      "people" to civName(house.civ),
      "seat" to placeName(house.seat),
      "founded" to house.foundedYear,
      "ended" to house.endedYear.takeIf { it != 0 },
      "founder" to figureName(house.founder),
      "exiledAs" to chronicle.civs.getOrNull(house.exiledTo)?.let { civName(house.exiledTo) },
      "members" to members.map {
        mapOf("name" to figureName(it.index), "role" to it.role.name.lowercase(), "born" to it.birthYear, "died" to it.deathYear.takeIf { d -> d != 0 })
      },
      events = members.flatMap { chronicle.eventsOf(Actor(ActorType.FIGURE, it.index)) }
        .filter { it.kind in HOUSE_EVENTS }
        .distinctBy { it.id }
        .sortedBy { it.year }
    )
  }

  private fun artifact(index: Int): Map<String, Any?> {
    val relic = chronicle.artifacts[index]
    val now = when {
      relic.restingSite >= 0 -> "rests in ${siteName(chronicle.sites[relic.restingSite])}"
      relic.keptAt >= 0 -> "kept in the temple of ${placeName(relic.keptAt)}"
      relic.holder >= 0 -> "carried by ${figureName(relic.holder)}"
      else -> "lost, nobody knows where"
    }
    return sheet(
      Actor(ActorType.ARTIFACT, index), "artifact", artifactName(index),
      "form" to relic.kind.name.lowercase(),
      "material" to relic.material,
      "forged" to relic.forgedYear,
      "smith" to figureName(relic.forgedBy),
      "forgedAt" to chronicle.settlements.firstOrNull { it.nameSeed == relic.forgedAtNameSeed }?.let { placeName(it.index) },
      "now" to now,
      "homeTemple" to relic.homeTemple.takeIf { it >= 0 }?.let(::placeName),
      events = chronicle.provenanceOf(index)
    )
  }

  /**
   * A war is not a record: it is a `WAR_DECLARED` event, and every battle of the war cites it as a cause. The
   * sieges, sackings and conquests cite the battles, so one more hop collects them.
   */
  private fun wars(): List<Map<String, Any?>> = chronicle.events
    .filter { it.kind == EventKind.WAR_DECLARED }
    .map { declared ->
      val sides = declared.actors.filter { it.type == ActorType.CIV }.map { it.index }
      val battles = chronicle.events.filter { it.kind == EventKind.BATTLE && declared.id in it.causes }
      val battleIds = battles.map { it.id }.toSet()
      val aftermath = chronicle.events.filter { e -> e.causes.any { it in battleIds } && e.kind in WAR_AFTERMATH }
      val peace = chronicle.events.firstOrNull {
        it.kind == EventKind.PEACE && it.year >= declared.year &&
            it.actors.filter { a -> a.type == ActorType.CIV }.map { a -> a.index }.toSet() == sides.toSet()
      }
      val events = (listOf(declared) + battles + aftermath + listOfNotNull(peace)).distinctBy { it.id }.sortedBy { it.year }
      val wins = battles.groupingBy { it.actors.first().index }.eachCount()
      sheet(
        null, "war", "the war of ${civName(sides[0])} and ${civName(sides[1])}, ${declared.year}",
        "ref" to "war#${declared.id}",
        "sides" to sides.map { side ->
          val civ = chronicle.civs[side]
          mapOf(
            "people" to civName(side),
            "capital" to placeName(civ.capital),
            "battlesWon" to (wins[side] ?: 0),
            "patronGod" to chronicle.deities.getOrNull(civ.patron)?.let { deityName(civ.patron, civ.cultureIndex) },
            "rulerAtStart" to rulerOf(side, declared.year)?.let(::figureName)
          )
        },
        "declared" to declared.year,
        "peace" to peace?.year,
        "fields" to battles.mapNotNull { b -> b.actors.firstOrNull { it.type == ActorType.SITE } }
          .distinct().map { siteName(chronicle.sites[it.index]) },
        events = events
      )
    }

  private fun battle(site: SiteRecord): Map<String, Any?> {
    val actor = Actor(ActorType.SITE, site.index)
    val events = chronicle.eventsOf(actor)
    return sheet(
      actor, "battle", siteName(site),
      "near" to site.settlement.takeIf { it >= 0 }?.let(::placeName),
      "firstBattle" to site.year,
      "battles" to site.battles,
      "fallen" to site.fallen,
      "peoples" to events.flatMap { e -> e.actors.filter { it.type == ActorType.CIV } }.distinct().map { civName(it.index) },
      "artifactLostHere" to chronicle.artifacts.getOrNull(site.artifact)?.let { artifactName(site.artifact) },
      events = events
    )
  }

  private fun deity(index: Int): Map<String, Any?> {
    val deity = chronicle.deities[index]
    val cultures = chronicle.civs.map { it.cultureIndex }.distinct().sorted()
    val actor = Actor(ActorType.DEITY, index)
    return sheet(
      actor, "deity", deityName(index, cultures.first()),
      "domain" to deity.domain.name.lowercase(),
      "namesByPeople" to chronicle.civs.map { mapOf("people" to civName(it.index), "name" to deityName(index, it.cultureIndex)) },
      "worshippedSince" to deity.year.takeIf { deity.cause >= 0 },
      "patronOf" to chronicle.civs.filter { it.patron == index }.map { civName(it.index) },
      "templesIn" to chronicle.settlements.filter { it.patron == index && it.wasFounded }.map { placeName(it.index) },
      "cults" to chronicle.cults.filter { it.deity == index }.map { cultName(it.index) },
      "orderLeaning" to deity.domain.leaning?.label,
      "sacredBeast" to deity.domain.sacred?.name?.lowercase(),
      events = chronicle.eventsOf(actor) + listOfNotNull(eventsById[deity.cause])
    )
  }

  private fun cult(index: Int): Map<String, Any?> {
    val cult = chronicle.cults[index]
    val actor = Actor(ActorType.CULT, index)
    return sheet(
      actor, "cult", cultName(index),
      "worships" to (chronicle.deities.getOrNull(cult.deity)?.let { deityName(cult.deity, cultureOfPlace(cult.origin)) }
        ?: cult.kind.name.lowercase()),
      "founder" to chronicle.figures.getOrNull(cult.founder)?.let { figureName(cult.founder) },
      "foundedAt" to placeName(cult.origin),
      "founded" to cult.foundedYear,
      "ended" to cult.endedYear.takeIf { it != 0 },
      "fate" to cult.fate.name.lowercase(),
      "towns" to cult.settlements.map(::placeName),
      "sanctuary" to chronicle.sites.getOrNull(cult.sanctuary)?.let(::siteName),
      events = chronicle.eventsOf(actor)
    )
  }

  private fun monster(index: Int): Map<String, Any?> {
    val monster = chronicle.monsters[index]
    val actor = Actor(ActorType.MONSTER, index)
    return sheet(
      actor, "monster", Names.monster(monster.nameSeed, monster.boss),
      "species" to monster.boss.title,
      "origin" to monster.origin.name.lowercase(),
      "awoke" to monster.awokeYear,
      "ended" to monster.endedYear.takeIf { it != 0 },
      "fate" to monster.fate.name.lowercase(),
      "hero" to chronicle.figures.getOrNull(monster.hero)?.let { figureName(monster.hero) },
      "place" to chronicle.sites.getOrNull(monster.site)?.let(::siteName),
      "trophy" to chronicle.artifacts.getOrNull(monster.trophy)?.let { artifactName(monster.trophy) },
      events = chronicle.eventsOf(actor)
    )
  }

  // --- Sheet assembly -------------------------------------------------------------------------------

  /**
   * Adds `names` and `years`: the whitelist the validator checks a text against. Every string field that is
   * a rendered name goes in, which is why the sheet is built from the same renderers as the event lines.
   */
  private fun sheet(
    actor: Actor?,
    kind: String,
    name: String,
    vararg fields: Pair<String, Any?>,
    events: List<HistoryEvent>
  ): Map<String, Any?> {
    val body = linkedMapOf<String, Any?>("ref" to actor?.toString(), "kind" to kind, "name" to name)
    for ((key, value) in fields) if (value != null) body[key] = value
    body["events"] = events.sortedBy { it.year }.map(::eventJson)

    val names = sortedSetOf<String>()
    val years = sortedSetOf<Int>()
    collect(body, names, years)
    body["names"] = names.toList()
    body["years"] = years.toList()
    return body
  }

  private fun collect(value: Any?, names: MutableSet<String>, years: MutableSet<Int>, key: String = "") {
    when (value) {
      is Map<*, *> -> value.forEach { (k, v) -> collect(v, names, years, k.toString()) }
      is List<*> -> value.forEach { collect(it, names, years, key) }
      is Int -> if (key in YEAR_KEYS) years.add(value)
      is String -> if (key in NAME_KEYS || key == "") names.add(value)
    }
  }

  private fun eventJson(event: HistoryEvent) = linkedMapOf(
    "id" to event.id,
    "year" to event.year,
    "kind" to event.kind.name.lowercase(),
    "text" to event.detail,
    "causes" to event.causes.mapNotNull { eventsById[it] }.map { "${it.year}: ${it.detail}" }
  )

  // --- Names, rendered the way the simulation renders them ------------------------------------------

  private fun civName(index: Int) = chronicle.civs[index].let { Names.civ(it.nameSeed, it.cultureIndex) }

  private fun figureName(index: Int) = chronicle.figures[index].let {
    Names.person(it.nameSeed, chronicle.civs[it.civ].cultureIndex, it.role)
  }

  private fun houseName(index: Int) = chronicle.dynasties[index].let {
    Names.house(it.nameSeed, chronicle.civs[it.civ].cultureIndex)
  }

  private fun artifactName(index: Int) = chronicle.artifacts[index].let {
    Names.artifact(it.nameSeed, chronicle.civs[chronicle.figures[it.forgedBy].civ].cultureIndex, it.kind, it.forgedAtNameSeed)
  }

  private fun deityName(index: Int, culture: Int) = chronicle.deities[index].let { Names.deity(it.nameSeed, culture, it.domain) }

  private fun cultName(index: Int): String {
    val cult = chronicle.cults[index]
    val deity = chronicle.deities.getOrNull(cult.deity)?.let { deityName(cult.deity, cultureOfPlace(cult.origin)) }
    return Names.cult(cult.nameSeed, cult.kind, deity, cult.beast)
  }

  private fun placeName(settlement: Int) =
    Names.place(chronicle.settlements[settlement].nameSeed, cultureOfPlace(settlement))

  /** Same culture as `ChronicleMain.cultureOf`, which is what the cult view renders a cult's god in. */
  private fun cultureOfPlace(settlement: Int) = siteCulture[settlement] ?: 0

  private fun cultureName(index: Int) = Culture.byIndex(index).name.lowercase()

  private fun rulerOf(civ: Int, year: Int): Int? = chronicle.figures
    .filter { it.civ == civ && it.role == FigureRole.RULER && it.birthYear <= year }
    .filter { it.deathYear == 0 || it.deathYear >= year }
    .maxByOrNull { it.birthYear }?.index

  /** Mirrors `HistorySim.siteName`, which is private to the simulation. */
  private fun siteName(site: SiteRecord): String {
    val of = when {
      site.monster >= 0 && (site.kind == SiteKind.LAIR || site.kind == SiteKind.SEAL) ->
        chronicle.monsters[site.monster].let { Names.monster(it.nameSeed, it.boss) }
      site.settlement >= 0 -> placeName(site.settlement)
      else -> "the wilds"
    }
    val form = when (site.kind) {
      SiteKind.RUIN -> "ruin"
      SiteKind.ASH_RUIN -> "ash"
      SiteKind.BATTLEFIELD -> "field"
      SiteKind.TOMB -> "barrow"
      SiteKind.SHRINE -> when (site.faction) {
        Faction.CHAOS -> "cairn"
        Faction.ETERNITY -> "ward"
        Faction.CIRCLE -> "circle"
        null -> "shrine"
      }
      else -> site.kind.name.lowercase().let { if (it == "monastery") "abbey" else if (it == "lighthouse") "light" else it }
    }
    return Names.site(site.nameSeed, of, form)
  }

  companion object {
    /** A civ's own log runs to hundreds of famines and eruptions. The sheet keeps what a chronicler would. */
    private const val CIV_EVENT_FLOOR = 55

    private val HOUSE_EVENTS = setOf(
      EventKind.SUCCESSION, EventKind.SUCCESSION_CRISIS, EventKind.DYNASTY_EXILED, EventKind.MARRIAGE_ALLIANCE,
      EventKind.FEUD_BEGAN, EventKind.VENGEANCE_SWORN, EventKind.VENGEANCE_FULFILLED, EventKind.BETRAYAL,
      EventKind.FIGURE_SLAIN
    )

    private val WAR_AFTERMATH = setOf(
      EventKind.SIEGE, EventKind.SETTLEMENT_SACKED, EventKind.SETTLEMENT_RAZED, EventKind.CONQUEST,
      EventKind.FIGURE_SLAIN, EventKind.BETRAYAL, EventKind.ARTIFACT_LOST, EventKind.ARTIFACT_PLUNDERED
    )

    private val YEAR_KEYS = setOf(
      "year", "founded", "ended", "born", "died", "forged", "declared", "peace", "firstBattle", "awoke",
      "worshippedSince"
    )

    private val NAME_KEYS = setOf(
      "name", "people", "home", "house", "parent", "spouse", "capital", "settlements", "patronGod", "houses",
      "against", "seat", "founder", "exiledAs", "smith", "forgedAt", "homeTemple", "fields", "near", "peoples",
      "artifactLostHere", "templesIn", "cults", "patronOf", "worships", "foundedAt", "towns", "sanctuary", "hero",
      "place", "trophy", "with", "artifactsMade", "rulerAtStart", "species", "order", "orderLeaning"
    )
  }
}
