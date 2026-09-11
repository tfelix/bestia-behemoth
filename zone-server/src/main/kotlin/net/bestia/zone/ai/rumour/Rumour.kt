package net.bestia.zone.ai.rumour

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.bestia.zone.ai.knowledge.Knowledge
import net.bestia.zone.ai.knowledge.Locality

/**
 * One piece of recent news, held by one settlement.
 *
 * ### A row per town that heard it, not per event
 *
 * News travels at walking pace here as it does in the generator, so one kill outside one village puts a
 * row in every settlement inside its reach and nowhere else. That is deliberately the opposite of
 * normalising: a shared event row plus a join table would be one truth about *where* a thing was heard,
 * and what a conversation asks is only ever "what does this town know", which this answers with a
 * primary-key range scan.
 *
 * ### Rows exist only while somebody would mention them
 *
 * Deleted at expiry rather than marked dead, so a world nobody has disturbed holds none at all and the
 * table is bounded by recent play instead of by the size of the map. `SettlementLedger` makes the same
 * trade for the same reason.
 *
 * ### Two version stamps, for `WorldObjectDivergence`'s reasons
 *
 * Settlement indices are dense and re-used, so a row that survived a reseed would not be orphaned - it
 * would be attached to *a different town*, and a player would be told about a battle outside a village
 * that has never existed.
 */
@Entity
@Table(name = "rumour")
class Rumour(
  @Id
  @Column(nullable = false)
  var id: Long = 0,

  @Column(nullable = false)
  var settlement: Int = 0,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  var kind: RumourKind = RumourKind.BOSS_SLAIN,

  /** Typed arguments for the phrasing, encoded - see [Slots]. */
  @Column(name = "slots", nullable = false, length = 1024)
  var slots: String = "",

  @Column(name = "posted_on_day", nullable = false)
  var postedOnDay: Double = 0.0,

  @Column(name = "expires_on_day", nullable = false)
  var expiresOnDay: Double = 0.0,

  /**
   * 0 to 1, how big a thing this was where it happened.
   *
   * Decides how far it travelled when posted, and then how much of the town holds it - a minor scuffle
   * reaches one village and one person in it.
   */
  @Column(nullable = false)
  var strength: Double = 0.0,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {

  fun slotMap(): Map<String, Knowledge.Slot> {
    return Slots.decode(slots)
  }

  /**
   * This news as a memory, indistinguishable downstream from one the chronicle produced.
   *
   * The join the whole design rests on: a conversation offers "the mountain took Karth" and "something
   * was killed outside the walls on the fourth" as the same kind of option, with one set of rules for
   * who holds them and one for how they are phrased.
   *
   * [Knowledge.topic] is the **negated** row id. Chronicle event ids are dense from zero and a topic id
   * is the only thing a conversation stores, so the two vocabularies have to be disjoint - and a
   * negative id cannot be mistaken for an event on any seed.
   *
   * Locality is always [Locality.NEARBY], which is what it is by construction: a town holds a row only
   * because the posting reached it, and that is exactly "close enough to have been seen or heard about".
   */
  fun toKnowledge(day: Double, presentYear: Int, variants: Int): Knowledge {
    return Knowledge(
      topic = -id.toInt(),
      key = KEY_PREFIX + kind.name,
      slots = slotMap(),
      importance = importanceOn(day),
      year = presentYear,
      locality = Locality.NEARBY,
      variants = variants,
    )
  }

  /**
   * How important this is *now*, which is not what it was worth when it happened.
   *
   * Linear decay to nothing at expiry. A week-old kill still ranks above a treaty signed two provinces
   * away and below the fire last night, which is the whole ordering a conversation needs from this.
   */
  fun importanceOn(day: Double): Int {
    val life = expiresOnDay - postedOnDay
    if (life <= 0.0) {
      return 0
    }

    val remaining = ((expiresOnDay - day) / life).coerceIn(0.0, 1.0)

    return (kind.baseImportance * strength * remaining).toInt()
  }

  fun hasExpired(day: Double): Boolean {
    return day >= expiresOnDay
  }

  companion object {
    /** Matches `HistoryKnowledge.KEY_PREFIX`'s shape, so the two read alike in a translation file. */
    const val KEY_PREFIX = "RUMOUR_"
  }

  /**
   * The slot map as one column.
   *
   * A column per slot name would need a migration every time a kind wants a different argument, and the
   * set of arguments is decided by a yml file that is expected to grow - `SettlementLedger` refuses
   * columns for the same reason. The type tag is part of the encoding because a name and a token are
   * both strings on the way out and mean opposite things: one is translated and one must never be.
   *
   * One line per slot, so a value may not contain a newline. Nothing can produce one - names come from
   * `Names`, which builds them from invented stems, and tokens are translation keys - so this is a note
   * for whoever first wants to put free text in a slot rather than a case to handle.
   */
  object Slots {

    fun encode(slots: Map<String, Knowledge.Slot>): String {
      return slots.entries.joinToString("\n") { (name, slot) ->
        when (slot) {
          is Knowledge.Slot.Name -> "$NAME$name=${slot.value}"
          is Knowledge.Slot.Token -> "$TOKEN$name=${slot.key}"
          is Knowledge.Slot.Number -> "$NUMBER$name=${slot.value}"
        }
      }
    }

    fun decode(text: String): Map<String, Knowledge.Slot> {
      return text.lineSequence()
        .filter { it.isNotBlank() && it.length > 1 }
        .mapNotNull { line ->
          val body = line.substring(1)
          val name = body.substringBefore('=')
          val value = body.substringAfter('=')

          when (line.first().toString()) {
            NAME -> name to Knowledge.Slot.Name(value)
            TOKEN -> name to Knowledge.Slot.Token(value)
            NUMBER -> value.toLongOrNull()?.let { name to Knowledge.Slot.Number(it) }
            else -> null
          }
        }
        .toMap()
    }

    private const val NAME = "N"
    private const val TOKEN = "T"
    private const val NUMBER = "#"
  }
}
