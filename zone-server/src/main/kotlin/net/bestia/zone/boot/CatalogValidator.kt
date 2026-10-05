package net.bestia.zone.boot

/** A check of the static content against the code. [ContentValidationBootRunner] runs every one. */
fun interface CatalogValidator {
  /** Throws when the content and the code disagree. */
  fun validate()
}
