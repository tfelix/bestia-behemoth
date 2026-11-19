package net.bestia.zone.text

import net.bestia.worldgen.history.NameLocale

/** A name of the chronicle in the languages that write it another way than English does. */
object LocalNames {

  /** @param render the name in one language, or null when the record is gone */
  fun of(english: String, render: (NameLocale) -> String?): Map<String, String> {
    return NameLocale.entries
      .filter { it != NameLocale.EN }
      .mapNotNull { locale -> render(locale)?.takeIf { it != english }?.let { locale.code to it } }
      .toMap()
  }
}
