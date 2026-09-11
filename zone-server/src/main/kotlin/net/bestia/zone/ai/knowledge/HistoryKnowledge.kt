package net.bestia.zone.ai.knowledge

import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.HistoryEvent
import net.bestia.worldgen.history.Names

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

  fun of(chronicle: Chronicle, event: HistoryEvent, locality: Locality): Knowledge {
    return Knowledge(
      topic = event.id,
      key = KEY_PREFIX + event.kind.name,
      slots = slotsOf(chronicle, event),
      importance = event.importance,
      year = event.year,
      locality = locality,
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
    val record = chronicle.settlements.getOrNull(index) ?: return unknown()

    return Knowledge.Slot.Name(Names.place(record.nameSeed, cultureOfSettlement(chronicle, index)))
  }

  private fun civName(chronicle: Chronicle, index: Int): Knowledge.Slot {
    val record = chronicle.civs.getOrNull(index) ?: return unknown()

    return Knowledge.Slot.Name(Names.civ(record.nameSeed, record.cultureIndex))
  }

  private fun figureName(chronicle: Chronicle, index: Int): Knowledge.Slot {
    val record = chronicle.figures.getOrNull(index) ?: return unknown()

    return Knowledge.Slot.Name(Names.person(record.nameSeed, cultureOfCiv(chronicle, record.civ), record.role))
  }

  private fun artifactName(chronicle: Chronicle, index: Int): Knowledge.Slot {
    val record = chronicle.artifacts.getOrNull(index) ?: return unknown()
    val culture = cultureOfCiv(chronicle, chronicle.figures.getOrNull(record.forgedBy)?.civ ?: -1)

    return Knowledge.Slot.Name(
      Names.artifact(record.nameSeed, culture, record.kind, record.forgedAtNameSeed)
    )
  }

  /**
   * Whose idiom names a town: whoever holds it, or whoever founded it once nobody does.
   *
   * A ruin keeps the name the people who built it gave it, which is the whole reason `foundingCiv`
   * outlives `ownerCiv` on the record.
   */
  private fun cultureOfSettlement(chronicle: Chronicle, index: Int): Int {
    val record = chronicle.settlements.getOrNull(index) ?: return -1
    val civ = record.ownerCiv.takeIf { it >= 0 } ?: record.foundingCiv

    return cultureOfCiv(chronicle, civ)
  }

  private fun cultureOfCiv(chronicle: Chronicle, civ: Int): Int {
    return chronicle.civs.getOrNull(civ)?.cultureIndex ?: -1
  }

  /**
   * A slot for an actor the chronicle does not have.
   *
   * A token rather than a name, because the one thing that must not happen is an English word reaching a
   * player through a slot that is documented as untranslatable.
   */
  private fun unknown(): Knowledge.Slot {
    return Knowledge.Slot.Token("NAME_UNKNOWN")
  }
}
