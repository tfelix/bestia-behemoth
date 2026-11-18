package net.bestia.zone.text.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable

/**
 * One text written for this world, in one language. A body is never changed once written: clients and the
 * api-server keep their copy for good.
 */
@Entity
@Table(name = "world_text")
class WorldText(
  @EmbeddedId
  var id: Key = Key(),

  /** Prose with the `{placeholders}` of its template. Null until written. */
  @Column(columnDefinition = "TEXT")
  var body: String? = null,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  var state: State = State.WAITING,

  @Column(length = 64)
  var model: String? = null,
) {

  @Embeddable
  data class Key(
    /** Random, so a client cannot read a text the game never gave it. */
    @Column(name = "text_id", nullable = false, length = 32)
    var textId: String = "",

    @Column(name = "locale", nullable = false, length = 8)
    var locale: String = "",
  ) : Serializable

  /** The rows are the queue of the text job: it writes the [WAITING] ones. */
  enum class State { WAITING, WRITTEN, REJECTED }
}
