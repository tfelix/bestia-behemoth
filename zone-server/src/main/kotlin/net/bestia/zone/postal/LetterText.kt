package net.bestia.zone.postal

import net.bestia.zone.postal.persistence.Letter
import net.bestia.zone.util.PlainText

/** A letter's subject and body as they may be stored. A body keeps its line breaks; nothing else invisible. */
data class LetterText(val subject: String, val body: String) {

  companion object {
    /** Null for a letter no client sends: an empty one, or one longer than the form allows. */
    fun of(subject: String, body: String): LetterText? {
      val cleanSubject = PlainText.withoutControlCharacters(subject).trim()
      val cleanBody = body.split('\n').joinToString("\n") { PlainText.withoutControlCharacters(it) }.trim()

      if (cleanSubject.isEmpty() && cleanBody.isEmpty()) return null
      if (cleanSubject.codePointCount(0, cleanSubject.length) > Letter.MAX_SUBJECT_LENGTH) return null
      if (cleanBody.codePointCount(0, cleanBody.length) > Letter.MAX_BODY_LENGTH) return null

      return LetterText(cleanSubject, cleanBody)
    }
  }
}
