package net.bestia.zone.ai.knowledge

import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.HistoryEvent

/**
 * A logged event, as something a person could say in any language.
 *
 * ### Why `HistoryEvent.detail` is not used
 *
 * The chronicle already carries a rendered sentence, and reaching for it is the obvious move. It is
 * English, baked at generation time, and there is no way to translate it afterwards - which is the
 * limitation `SettlementLoreService` records as permanent. It is only permanent for the *sentence*: the
 * parts it was built from are all still here, as an event kind and typed indices into five tables, so a
 * memory can be rebuilt as a key plus its arguments instead and translated like anything else.
 *
 * `detail` stays where it is, for the chronicle tool and for logs.
 *
 * ### Slots come from the actors, not from a table per kind
 *
 * Every event names who it happened to, so the arguments a kind can offer follow from its actors rather
 * than from a mapping somebody has to keep in step with the enum. A phrasing uses whichever of them it
 * needs and ignores the rest.
 */
object HistoryKnowledge {

  const val KEY_PREFIX = "HISTORY_"

  const val SLOT_PLACE = "place"
  const val SLOT_CIV = "civ"
  const val SLOT_FIGURE = "figure"
  const val SLOT_ARTIFACT = "artifact"
  const val SLOT_YEAR = "year"

  /** How long ago it was, in words. Always produced, so any phrasing may reach for it. See [Era]. */
  const val SLOT_ERA = "era"

  /**
   * What fills a slot whose actor the chronicle has no name for.
   *
   * One per slot rather than one between them, because each stands in a different grammatical position -
   * a civ follows "The", a place follows "at" - and a single filler reads wrong in three of the four.
   */
  const val UNKNOWN_PLACE = "NAME_UNKNOWN_PLACE"
  const val UNKNOWN_CIV = "NAME_UNKNOWN_CIV"
  const val UNKNOWN_FIGURE = "NAME_UNKNOWN_FIGURE"
  const val UNKNOWN_ARTIFACT = "NAME_UNKNOWN_ARTIFACT"

  fun of(chronicle: Chronicle, event: HistoryEvent, locality: Locality, variants: Int = 1): Knowledge {
    return Knowledge(
      topic = event.id,
      key = KEY_PREFIX + event.kind.name,
      slots = slotsOf(chronicle, event),
      importance = event.importance,
      year = event.year,
      locality = locality,
      variants = variants,
    )
  }

  /**
   * The first actor of each type, by the log's own convention that the first actor is the subject.
   *
   * A siege naming two civilisations offers the attacker, because that is the one a sentence about a
   * siege wants. Naming both would need a second slot per type and a phrasing that knows which way round
   * they are, and no line has yet wanted it.
   */
  private fun slotsOf(chronicle: Chronicle, event: HistoryEvent): Map<String, Knowledge.Slot> {
    val slots = LinkedHashMap<String, Knowledge.Slot>()
    slots[SLOT_YEAR] = Knowledge.Slot.Number(event.year.toLong())
    slots[SLOT_ERA] = Knowledge.Slot.Token(Era.of(chronicle.presentYear - event.year).key)

    for (actor in event.actors) {
      when (actor.type) {
        ActorType.SETTLEMENT -> slots.putIfAbsent(SLOT_PLACE, placeName(chronicle, actor.index))
        ActorType.CIV -> slots.putIfAbsent(SLOT_CIV, civName(chronicle, actor.index))
        ActorType.FIGURE -> slots.putIfAbsent(SLOT_FIGURE, figureName(chronicle, actor.index))
        ActorType.ARTIFACT -> slots.putIfAbsent(SLOT_ARTIFACT, artifactName(chronicle, actor.index))

        // A site's name is "the barrow of X" - the form word is English, so it cannot be handed over as a
        // proper noun the way the others can. Left out until the form is a token beside the name.
        ActorType.SITE -> Unit
      }
    }

    return slots
  }

  private fun placeName(chronicle: Chronicle, index: Int): Knowledge.Slot {
    return named(ChronicleNames.placeOf(chronicle, index), UNKNOWN_PLACE)
  }

  private fun civName(chronicle: Chronicle, index: Int): Knowledge.Slot {
    return named(ChronicleNames.civOf(chronicle, index), UNKNOWN_CIV)
  }

  private fun figureName(chronicle: Chronicle, index: Int): Knowledge.Slot {
    return named(ChronicleNames.figureOf(chronicle, index), UNKNOWN_FIGURE)
  }

  private fun artifactName(chronicle: Chronicle, index: Int): Knowledge.Slot {
    return named(ChronicleNames.artifactOf(chronicle, index), UNKNOWN_ARTIFACT)
  }

  /**
   * A name, or a token standing in for one the chronicle does not have.
   *
   * A token rather than an English word, because the one thing that must not happen is an English word
   * reaching a player through a slot that is documented as untranslatable.
   */
  private fun named(name: String?, unknownKey: String): Knowledge.Slot {
    return if (name == null) Knowledge.Slot.Token(unknownKey) else Knowledge.Slot.Name(name)
  }
}
