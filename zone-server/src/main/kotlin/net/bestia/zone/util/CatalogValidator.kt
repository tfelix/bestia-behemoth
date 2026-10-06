package net.bestia.zone.util

/** A check of the static content against the code. [net.bestia.zone.boot.ContentValidationBootRunner] runs every one. */
fun interface CatalogValidator {
  /** Throws when the content and the code disagree. */
  fun validate()
}
