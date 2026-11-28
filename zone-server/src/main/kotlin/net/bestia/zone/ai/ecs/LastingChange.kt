package net.bestia.zone.ai.ecs

/**
 * Reports a key's new value once it has held for [holdMs], so a value that flicks back and forth reports nothing.
 * The first value held is reported too, as a change from null.
 */
class LastingChange<K, V : Any>(private val holdMs: Long) {

  data class Change<V>(val from: V?, val to: V)

  private class Track<V>(var reported: V?, var candidate: V, var since: Long)

  private val tracks = HashMap<K, Track<V>>()

  fun see(key: K, value: V, at: Long): Change<V>? {
    val track = tracks.getOrPut(key) { Track(null, value, at) }
    if (value != track.candidate) {
      track.candidate = value
      track.since = at
    }
    if (track.candidate == track.reported || at - track.since < holdMs) return null

    val change = Change(track.reported, track.candidate)
    track.reported = track.candidate
    return change
  }

  fun retainAll(keys: Set<K>) {
    tracks.keys.retainAll(keys)
  }
}
