package net.bestia.bus

/**
 * One entry as read back from a stream. [id] is the stream entry id, which orders entries and lets a reader
 * resume after it.
 */
data class StreamEntry<T : Any>(val id: String, val payload: T)
