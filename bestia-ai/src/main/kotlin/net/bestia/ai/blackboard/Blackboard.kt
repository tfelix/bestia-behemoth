package net.bestia.ai.blackboard

import java.time.Instant

class Blackboard(
    private val id: String
) {

  data class Entry<T>(
      val key: String,
      var created: Instant,
      var lastAccessed: Instant,
      var data: T
  ) {

    companion object {
      fun <T> create(key: String, data: T): Entry<T> {
        return Entry(
            key = key,
            created = Instant.now(),
            lastAccessed = Instant.now(),
            data = data
        )
      }
    }
  }

  private val entries: MutableMap<String, Entry<*>> = mutableMapOf()

  fun getEntry(key: String): Entry<*>? {
    return entries[key]
  }

  fun getOrDefaultEntry(key: String, default: Any): Entry<*> {
    return entries.getOrElse(key) {
      Entry.create(key, default)
    }
  }

  fun setEntry(entry: Entry<*>) {
    entry.lastAccessed = Instant.now()
    entries[entry.key] = entry
  }

  fun deleteEntry(key: String) {
    entries.remove(key)
  }
}
