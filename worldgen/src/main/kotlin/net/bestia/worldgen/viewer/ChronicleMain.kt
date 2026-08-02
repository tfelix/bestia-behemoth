package net.bestia.worldgen.viewer

import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.Timings
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.pipeline.StandardWorld
import java.util.Locale

/**
 * Reads the world's history.
 *
 * ### Why a text tool and not a view
 *
 * A chronicle is prose. The other debugging tools in this package are visual because what they show is
 * spatial - a map, a cross-section, a town plan - and history is not: the questions asked of it are "what
 * happened here", "who did this", "where did this sword come from", "why is there a ruin on this hill".
 * Every one of those is answered by a list of sentences in year order, and rendering it would only make it
 * harder to read.
 *
 * What *is* spatial about history - ruins, battlefields, tombs, monuments - already appears in the world
 * viewer, because the exhaustive `when` in [MapRenderer.colorOf] forced a colour for each of them the moment
 * the feature kinds existed.
 *
 * ```
 * ./gradlew :worldgen:chronicle                    # the shape of this world's history
 * ./gradlew :worldgen:chronicle -Ptop=60           # the sixty most important events
 * ./gradlew :worldgen:chronicle -Pyear=430         # the world as it stood in one year
 * ./gradlew :worldgen:chronicle -Pcivs             # every civilisation, its span and its grudges
 * ./gradlew :worldgen:chronicle -Partifacts        # every artifact and its provenance chain
 * ./gradlew :worldgen:chronicle -Pfigures          # everybody the log remembers
 * ./gradlew :worldgen:chronicle -Psites            # ruins, battlefields, tombs and monuments
 * ./gradlew :worldgen:chronicle -Pquests           # unresolved threads worth turning into quests
 * ./gradlew :worldgen:chronicle -Pall              # everything, at length
 * ```
 */
object ChronicleMain {

  @JvmStatic
  fun main(args: Array<String>) {
    val cli = WorldArgs(args.toList(), extraFlags = CHRONICLE_FLAGS)
    val config = cli.worldConfig(
      StandardWorld.demoConfig().copy(widthCells = 192, heightCells = 192)
    )

    val tuning = cli.tuning()
    println("world ${WorldArgs.summary(config)}")
    println("  ${tuning.summary()}")
    val generated = StandardWorld.build(config, params = tuning.params)
    Timings.printAndReset()
    val chronicle = generated.world.chronicle

    if (chronicle.events.isEmpty()) {
      println()
      println("this world has no history: no settlement site was ever founded")
      return
    }

    val all = cli.has("--all")
    summary(chronicle)

    if (all || cli.has("--civs")) civs(chronicle)
    if (all || cli.value("--year") != null) {
      standing(chronicle, cli.int("--year") ?: chronicle.presentYear)
    }
    if (all || cli.has("--figures")) figures(chronicle)
    if (all || cli.has("--artifacts")) artifacts(chronicle)
    if (all || cli.has("--sites")) sites(chronicle)
    if (all || cli.has("--quests")) quests(chronicle)

    top(chronicle, cli.int("--top") ?: DEFAULT_TOP)
  }

  /** The shape of a world's history in a dozen lines. What to read first. */
  private fun summary(chronicle: Chronicle) {
    println()
    println("=== ${chronicle.startYear} to ${chronicle.presentYear} ===")
    line("events kept", "${chronicle.events.size}")
    line(
      "events pruned", "${chronicle.prunedEvents} " +
          "(${percent(chronicle.prunedEvents, chronicle.events.size + chronicle.prunedEvents)} of the log)"
    )
    line("civilisations", "${chronicle.civs.size}, ${chronicle.civs.count { it.exists }} surviving")
    line(
      "settlements",
      "${chronicle.settlements.count { it.wasFounded }} founded, " +
          "${chronicle.settlements.count { it.isRuin }} lost, " +
          "${chronicle.settlements.count { !it.wasFounded }} never settled"
    )
    line("notable figures", "${chronicle.figures.size}")
    line(
      "artifacts",
      "${chronicle.artifacts.size}, ${chronicle.artifacts.count { it.restingSite >= 0 }} lost or entombed"
    )
    line("sites", chronicle.sites.groupingBy { it.kind }.eachCount().entries
      .sortedByDescending { it.value }
      .joinToString(", ") { "${it.value} ${it.key.name.lowercase()}" }
      .ifEmpty { "none" })

    println()
    println("what happened, by kind")
    for ((kind, count) in chronicle.eventCensus()) {
      line("  ${kind.name.lowercase()}", "$count")
    }
  }

  private fun top(chronicle: Chronicle, count: Int) {
    println()
    println("the $count most important events")
    for (event in chronicle.topEvents(count).sortedWith(compareBy({ it.year }, { it.id }))) {
      println("  ${event.year.toString().padStart(5)}  ${event.detail}")
    }
  }

  private fun civs(chronicle: Chronicle) {
    println()
    println("civilisations")
    for (civ in chronicle.civs) {
      val name = Names.civ(civ.nameSeed, civ.cultureIndex)
      val span = if (civ.exists) "${civ.foundedYear}-" else "${civ.foundedYear}-${civ.endedYear}"
      val standing = civ.settlements.count { chronicle.settlementStood(it, chronicle.presentYear) }
      val people = civ.settlements
        .filter { chronicle.settlementStood(it, chronicle.presentYear) }
        .sumOf { chronicle.settlements[it].population }

      println()
      println("  $name (${Culture.byIndex(civ.cultureIndex).name}), $span")
      line("    settlements", "$standing standing of ${civ.settlements.size} ever held")
      line("    people", "$people, peak ${civ.peakPopulation}")
      line("    technology", fixed(civ.technology))
      line(
        "    capital",
        Names.place(chronicle.settlements[civ.capital].nameSeed, civ.cultureIndex)
      )

      if (civ.grudges.isEmpty()) {
        line("    grudges", "none")
      } else {
        println("    grudges")
        // A grudge cites an event, which is the point of storing it that way - an NPC can name the wrong.
        for ((other, eventId) in civ.grudges.distinctBy { it.first }) {
          val event = chronicle.events.firstOrNull { it.id == eventId }
          println(
            "      against ${Names.civ(chronicle.civs[other].nameSeed, chronicle.civs[other].cultureIndex)}" +
                (event?.let { ": ${it.year}, ${it.detail}" } ?: "")
          )
        }
      }
    }
  }

  /**
   * The world as it stood in one year: the history scrub the architecture document's tooling section asks
   * for, in the form the data supports.
   *
   * Not an animation. Every settlement carries a founding year and an abandonment year, so "did this place
   * exist then" is a comparison, and that is the whole of what a scrub needs to answer. Rendering it as a
   * map per year would be a fourth view of the same comparison.
   */
  private fun standing(chronicle: Chronicle, year: Int) {
    val clamped = year.coerceIn(chronicle.startYear, chronicle.presentYear)
    val standing = chronicle.settlements.filter { chronicle.settlementStood(it.index, clamped) }

    println()
    println("=== the world in year $clamped ===")
    line("settlements standing", "${standing.size}")
    line(
      "founded since",
      "${chronicle.settlements.count { it.wasFounded && it.foundedYear > clamped }} still to come"
    )
    line("already lost", "${chronicle.settlements.count { it.isRuin && it.abandonedYear <= clamped }}")

    println()
    println("the ten largest then")
    standing.sortedByDescending { it.population }.take(10).forEach {
      println(
        "  ${Names.place(it.nameSeed, cultureOf(chronicle, it.index)).padEnd(20)} " +
            "founded ${it.foundedYear}, ${it.population} today"
      )
    }

    val thatYear = chronicle.eventsIn(clamped - 5..clamped + 5)
    if (thatYear.isNotEmpty()) {
      println()
      println("within five years either side")
      thatYear.sortedBy { it.year }.forEach { println("  ${it.year.toString().padStart(5)}  ${it.detail}") }
    }
  }

  private fun figures(chronicle: Chronicle) {
    println()
    println("${chronicle.figures.size} notable figures")
    for (figure in chronicle.figures) {
      val civ = chronicle.civs[figure.civ]
      val died = if (figure.deathYear == 0) "living" else "d. ${figure.deathYear}"
      println(
        "  ${Names.person(figure.nameSeed, civ.cultureIndex, figure.role).padEnd(30)} " +
            "${figure.role.name.lowercase().padEnd(9)} " +
            "b. ${figure.birthYear}, $died, ${Names.civ(civ.nameSeed, civ.cultureIndex)}" +
            if (figure.restingSite >= 0) ", buried in a barrow" else ""
      )
    }
  }

  /**
   * Every artifact and the chain of events that put it where it is.
   *
   * The chain is the payoff of the whole history stage, and the reason to read this view: an artifact whose
   * chain ends `entombed in the tomb of X` is a thing a player can go and dig up, and the tomb is a real
   * marker at a real position because the same simulation put it there.
   */
  private fun artifacts(chronicle: Chronicle) {
    println()
    println("${chronicle.artifacts.size} artifacts")
    for (relic in chronicle.artifacts) {
      val smith = chronicle.figures[relic.forgedBy]
      val culture = chronicle.civs[smith.civ].cultureIndex

      println()
      println("  ${Names.artifact(relic.nameSeed, culture, relic.kind, relic.forgedAtNameSeed)} - ${relic.material}")
      for (event in chronicle.provenanceOf(relic.index)) {
        println("    ${event.year.toString().padStart(5)}  ${event.detail}")
      }
      if (relic.restingSite >= 0) {
        val site = chronicle.sites[relic.restingSite]
        // The depth is printed only where there is one, which today means only a hoard. An artifact in a
        // cave is findable and one in a barrow is diggable, and the difference is worth a reader seeing.
        val depth = if (site.elevation.isNaN()) "" else " at ${site.elevation.toInt()} m underground"
        println(
          "    rests at (${site.position.x.toInt()}, ${site.position.y.toInt()})$depth " +
              "in a ${site.kind.name.lowercase()}, decay ${fixed(site.decay)}"
        )
      } else {
        println("    still in use")
      }
    }
  }

  private fun sites(chronicle: Chronicle) {
    println()
    println("${chronicle.sites.size} sites history left behind")
    for (kind in SiteKind.entries) {
      val of = chronicle.sitesOfKind(kind)
      if (of.isEmpty()) continue

      println()
      println("  ${of.size} ${kind.name.lowercase()}")
      of.sortedBy { it.year }.take(SITES_PER_KIND).forEach {
        println(
          "    year ${it.year.toString().padStart(4)}  " +
              "(${it.position.x.toInt()}, ${it.position.y.toInt()})  " +
              "r=${it.radius.toInt()} m  decay ${fixed(it.decay)}"
        )
      }
      if (of.size > SITES_PER_KIND) println("    ... and ${of.size - SITES_PER_KIND} more")
    }
  }

  /**
   * Unresolved threads: the quest hooks the architecture document says to mine the log for.
   *
   * Deliberately mechanical. A thread is unresolved when the log contains a beginning with no matching end -
   * an artifact taken and never recovered, a monster driven off, a settlement lost with nobody left who
   * remembers it. Nothing here writes a quest; it lists the places the log has left a question open, which is
   * the raw material and the part that has to come out of the simulation rather than out of a template.
   */
  private fun quests(chronicle: Chronicle) {
    println()
    println("unresolved threads")

    val lost = chronicle.artifacts.filter { relic ->
      relic.restingSite >= 0 &&
          chronicle.provenanceOf(relic.index).none { it.kind == EventKind.ARTIFACT_ENTOMBED }
    }
    println()
    println("  ${lost.size} artifacts lost rather than laid to rest")
    lost.take(THREADS_PER_KIND).forEach { relic ->
      val culture = chronicle.civs[chronicle.figures[relic.forgedBy].civ].cultureIndex
      val site = chronicle.sites[relic.restingSite]
      println(
        "    ${Names.artifact(relic.nameSeed, culture, relic.kind, relic.forgedAtNameSeed)} " +
            "at (${site.position.x.toInt()}, ${site.position.y.toInt()})"
      )
    }

    val razed = chronicle.settlements.filter { it.isRuin && it.ruinCause == EventKind.SETTLEMENT_RAZED }
    println()
    println("  ${razed.size} settlements destroyed by war, whose ruins nobody has reclaimed")
    razed.take(THREADS_PER_KIND).forEach {
      println(
        "    ${Names.place(it.nameSeed, cultureOf(chronicle, it.index))}, " +
            "razed ${it.abandonedYear}, ${it.timesSacked} sackings"
      )
    }

    val fallen = chronicle.civs.filter { !it.exists }
    println()
    println("  ${fallen.size} civilisations that no longer exist")
    fallen.take(THREADS_PER_KIND).forEach {
      println(
        "    ${Names.civ(it.nameSeed, it.cultureIndex)}, ${it.foundedYear}-${it.endedYear}, " +
            "peak ${it.peakPopulation}"
      )
    }

    val unmarked = chronicle.figures.filter { it.deathYear != 0 && it.restingSite < 0 }
    println()
    println("  ${unmarked.size} figures with no known grave")
    unmarked.take(THREADS_PER_KIND).forEach {
      val civ = chronicle.civs[it.civ]
      println("    ${Names.person(it.nameSeed, civ.cultureIndex, it.role)}, d. ${it.deathYear}")
    }
  }

  private fun cultureOf(chronicle: Chronicle, settlement: Int): Int =
    chronicle.settlements[settlement].ownerCiv.takeIf { it >= 0 }
      ?.let { chronicle.civs[it].cultureIndex }
      ?: chronicle.settlements[settlement].foundingCiv.takeIf { it >= 0 }
        ?.let { chronicle.civs[it].cultureIndex }
      ?: 0

  private fun line(label: String, value: String) = println("  ${label.padEnd(20)} $value")

  private fun fixed(value: Double) = "%.2f".format(Locale.ROOT, value)

  private fun percent(n: Int, total: Int) =
    if (total == 0) "0%" else "%.0f%%".format(Locale.ROOT, 100.0 * n / total)

  private const val DEFAULT_TOP = 30
  private const val SITES_PER_KIND = 12
  private const val THREADS_PER_KIND = 8

  private val CHRONICLE_FLAGS = setOf(
    "--top", "--year", "--civs", "--artifacts", "--figures", "--sites", "--quests", "--all"
  )
}
