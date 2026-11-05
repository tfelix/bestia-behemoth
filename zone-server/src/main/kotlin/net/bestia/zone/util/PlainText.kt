package net.bestia.zone.util

/** What text a player writes for other players may hold, whether a chat line or a letter. */
object PlainText {

  private val INVISIBLE_FORMATTING =
    ('​'..'‏') + ('‪'..'‮') + ('⁦'..'⁩') + '﻿'

  /** Control and direction-override characters only ever garble, or disguise, someone else's text. */
  fun withoutControlCharacters(text: String): String {
    return text.filterNot { Character.isISOControl(it) || it in INVISIBLE_FORMATTING }
  }
}
