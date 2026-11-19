package net.bestia.zone.text.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.io.Serializable

/**
 * One text written for this world, in one language. A body is never changed once written: clients and the
 * api-server keep their copy for good.
 *
 * The rows are also the queue of the text job. A wanted text is a [State.WAITING] row, so the job can stop at any
 * time and go on later.
 */
@Entity
@Table(
  name = "world_text",
  uniqueConstraints = [UniqueConstraint(columnNames = ["subject", "locale"])],
  indexes = [Index(columnList = "locale, state, priority")],
)
class WorldText(
  @EmbeddedId
  var id: Key = Key(),

  /** What the text is about, by a name that is stable in this world, such as `grave:7`. See `TextSubject`. */
  @Column(nullable = false, length = 64)
  var subject: String = "",

  /** The job writes lower numbers first, so the texts players meet first exist when the time runs out. */
  @Column(nullable = false)
  var priority: Int = 0,

  /** Prose with the `{placeholders}` of its template. Null until written. */
  @Column(columnDefinition = "TEXT")
  var body: String? = null,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  var state: State = State.WAITING,

  @Column(length = 64)
  var model: String? = null,

  /** How often the job asked the model for this text. */
  @Column(nullable = false)
  var attempts: Int = 0,
) {

  @Embeddable
  data class Key(
    /** Random, so a client cannot read a text the game never gave it. The same in every language. */
    @Column(name = "text_id", nullable = false, length = 32)
    var textId: String = "",

    @Column(name = "locale", nullable = false, length = 8)
    var locale: String = "",
  ) : Serializable

  enum class State {
    WAITING,
    WRITTEN,

    /** The model did not get it right in the tries it has. The template stays. */
    REJECTED,
  }
}
