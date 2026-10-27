package net.bestia.zone.notebook

/**
 * An entry of [collection] was written or [deleted], and the collection is now at [revision]. Published inside
 * the writing transaction; listen after the commit. [body] is the entry as clients are shown it, null when deleted.
 * [masterId] is the master the entry belongs to, null for a collection of the whole account.
 */
data class NotebookChangedEvent(
  val accountId: Long,
  val masterId: Long?,
  val collection: String,
  val key: String,
  val revision: Long,
  val deleted: Boolean,
  val body: String?,
)
