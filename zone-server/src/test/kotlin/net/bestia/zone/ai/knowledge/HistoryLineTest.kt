package net.bestia.zone.ai.knowledge

import net.bestia.worldgen.core.EventKind
import net.bestia.zone.world.GeneratedWorlds
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * That every phrasing can actually be filled in, on real worlds.
 *
 * The catalogue says which placeholders a kind's lines may use, and a placeholder the events do not
 * carry renders as a literal brace at the player - in one locale, in a line nobody on the team reads,
 * about an event kind that turns up on one seed in twenty. Nothing else would catch it.
 *
 * The trap is specific and was hit while authoring: an eruption happens to a *mountain*. It names no
 * settlement, no people and nobody at all, so a line about one cannot say where it was however much it
 * would like to. Several kinds are like that, and a few more carry an actor only sometimes - the
 * abandon family attaches a civilisation only where one still held the place.
 */
class HistoryLineTest {

  @Test
  fun `every slot a kind declares is one its events actually carry`() {
    val catalogue = HistoryLineCatalogue().also { it.load() }
    val seen = HashMap<EventKind, Int>()
    val problems = mutableListOf<String>()

    for (seed in SEEDS) {
      val world = GeneratedWorlds.of(seed)
      val chronicle = world.world.chronicle

      for (event in chronicle.events) {
        seen.merge(event.kind, 1, Int::plus)

        val produced = HistoryKnowledge.of(chronicle, event, Locality.NEARBY).slots.keys
        val declared = catalogue.of(event.kind).slots
        val missing = declared - produced

        if (missing.isNotEmpty()) {
          problems.add(
            "${event.kind} declares ${missing.joinToString(", ")} but event ${event.id} on seed $seed " +
              "carries only ${produced.joinToString(", ")}"
          )
        }
      }
    }

    // Printed rather than asserted: which kinds a world happens to log is a roll, and a kind that
    // never came up is not evidence of anything. It is worth seeing, because it is also the list of
    // phrasings no test has ever filled in.
    val never = EventKind.entries.filter { seen[it] == null }
    println("never logged on ${SEEDS.size} worlds, so unexercised: ${never.joinToString(", ") { it.name }}")

    assertTrue(seen.isNotEmpty(), "no events at all on any world")
    assertTrue(
      problems.isEmpty(),
      "phrasings declare placeholders their events cannot fill:\n" + problems.distinct().take(20).joinToString("\n")
    )
  }

  /** A kind with no phrasing is an event class nobody in the world can mention. */
  @Test
  fun `the catalogue voices every event kind`() {
    val catalogue = HistoryLineCatalogue().also { it.load() }

    for (kind in EventKind.entries) {
      val line = catalogue.of(kind)
      assertTrue(line.variants >= 1, "$kind has no phrasing")
    }
  }

  private companion object {
    val SEEDS = GeneratedWorlds.SEEDS
  }
}
