package net.bestia.zone.postal

import net.bestia.zone.postal.persistence.Letter
import java.time.Instant

/** A letter as clients are shown it. */
data class LetterView(
  val letterId: Long,
  val senderName: String,
  val subject: String,
  val body: String,
  val sentAt: Instant,
  val expiresAt: Instant,
  val read: Boolean,
) {

  companion object {
    fun of(letter: Letter): LetterView {
      return LetterView(
        letterId = letter.id,
        senderName = letter.senderName,
        subject = letter.subject,
        body = letter.body,
        sentAt = letter.sentAt,
        expiresAt = letter.expiresAt,
        read = letter.read,
      )
    }
  }
}
