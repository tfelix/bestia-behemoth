package net.bestia.zone.ai.knowledge

/**
 * One thing somebody can be told, whatever produced it.
 *
 * The world's history produces these, and so will a rumour posted last Tuesday and a fact read off the
 * scorch registry. Nothing downstream is allowed to tell the three apart - that is what lets a
 * conversation offer "the mountain took Karth" and "a master rode through on the fourth" as the same kind
 * of option, with one set of rules for who holds them and one for how they are phrased.
 *
 * Deliberately carries no sentence. [key] is a translation key stem and [slots] are its arguments, so a
 * memory survives being read in a language nobody wrote it in. See the dialogue design document.
 */
class Knowledge(
  /**
   * What a conversation option points at, stable for as long as the world is.
   *
   * A chronicle event's id. It has to be reproducible rather than a list position, because an option id
   * is the *only* thing a conversation stores - see the design's argument for statelessness.
   */
  val topic: Int,
  /** Key stem, e.g. `HISTORY_ERUPTION`. The speaker picks the variant suffix. */
  val key: String,
  val slots: Map<String, Slot>,
  /** 0 to 100, on `EventKind.baseImportance`'s scale, whatever produced this. */
  val importance: Int,
  /** Chronicle year, or the present year for something that happened in play. */
  val year: Int,
  val locality: Locality,
  /**
   * How many phrasings exist for this, `<key>_1` upward.
   *
   * Carried rather than looked up, so that whoever produced a memory is the one who knows how it can be
   * said - a rumour posted last Tuesday and a thousand-year-old founding answer that question from
   * completely different places, and the layer that picks one must not have to know which is which.
   */
  val variants: Int = 1,
) {

  override fun toString(): String {
    return "$key$slots imp=$importance $locality"
  }

  /**
   * One argument filled into a memory's phrasing.
   *
   * Narrower than the dialog package's `DialogArg` on purpose, and not merely a copy of it: what a memory
   * can supply is a name, a word to look up, or a count, and nothing here should ever be able to name an
   * item or an entity. The mapping to the wire type is a `when` in whoever sends it.
   */
  sealed interface Slot {

    /**
     * A proper noun, already rendered.
     *
     * Passing the text is right rather than a shortcut: `Names` builds these out of invented stems that
     * belong to no language, so "Karth" is Karth in every locale and there is nothing to translate.
     */
    data class Name(val value: String) : Slot

    /** A translation key the reader resolves in turn, for the words around the names. */
    data class Token(val key: String) : Slot

    data class Number(val value: Long) : Slot
  }
}
