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

  /** The other side, where an event has two of one kind of actor. Only ever a second civilisation. */
  const val SLOT_FOE = "foe"

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
   * A second civilisation is the one exception, because a war has two sides and a line about one that
   * can only name the winner is half a sentence. Only civs: no other type is ever logged twice with
   * both worth saying.
   */
  private fun slotsOf(chronicle: Chronicle, event: HistoryEvent): Map<String, Knowledge.Slot> {
    val slots = LinkedHashMap<String, Knowledge.Slot>()
    slots[SLOT_YEAR] = Knowledge.Slot.Number(event.year.toLong())
    slots[SLOT_ERA] = Knowledge.Slot.Token(Era.of(chronicle.presentYear - event.year).key)

    for (actor in event.actors) {
      when (actor.type) {
        ActorType.SETTLEMENT -> slots.putIfAbsent(SLOT_PLACE, placeName(chronicle, actor.index))
        ActorType.CIV -> if (slots.putIfAbsent(SLOT_CIV, civName(chronicle, actor.index)) != null) {
          slots.putIfAbsent(SLOT_FOE, civName(chronicle, actor.index))
        }
        ActorType.FIGURE -> slots.putIfAbsent(SLOT_FIGURE, figureName(chronicle, actor.index))
        ActorType.ARTIFACT -> slots.putIfAbsent(SLOT_ARTIFACT, artifactName(chronicle, actor.index))

        // Not the site's own name - "the barrow of X" carries an English form word that cannot be handed
        // over as a proper noun. What is worth having is the town it was built by, which every raised
        // site has and which is the thing a player actually asked: where is it. A wound in the world
        // belongs to no town and still says nothing.
        ActorType.SITE -> hostOf(chronicle, actor.index)
          ?.let { slots.putIfAbsent(SLOT_PLACE, placeName(chronicle, it)) }
      }
    }

    return slots
  }

  /** The settlement a site was raised by, or null for one that belongs to nobody. */
  private fun hostOf(chronicle: Chronicle, site: Int): Int? {
    return chronicle.sites.getOrNull(site)?.settlement?.takeIf { it >= 0 }
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
