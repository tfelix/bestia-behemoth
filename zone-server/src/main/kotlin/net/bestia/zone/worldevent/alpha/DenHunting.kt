package net.bestia.zone.worldevent.alpha

import net.bestia.zone.world.WorldScopedData
import net.bestia.zone.worldevent.persistence.HuntedDen
import net.bestia.zone.worldevent.persistence.HuntedDenRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap

/**
 * When each den was last hunted, by den feature id and Bestia second. Kills arrive on the tick and only touch the
 * map; the rows are read and written off it. A den nobody was seen hunting yet counts from when it was first asked
 * about, so a new world, or this feature arriving on an old one, does not raise an alpha everywhere at once.
 */
@Component
class DenHunting(
  private val repository: HuntedDenRepository,
) : WorldScopedData {

  private val lastHunted = ConcurrentHashMap<Long, Long>()
  private val dirty = ConcurrentHashMap.newKeySet<Long>()

  @Volatile
  private var loaded = false

  /** On the tick. */
  fun hunted(denFeatureId: Long, atSecond: Long) {
    lastHunted.merge(denFeatureId, atSecond, ::maxOf)
    dirty += denFeatureId
  }

  /** How long each den has gone unhunted at Bestia second [now]. Off the tick. */
  fun unhuntedSeconds(denFeatureIds: Collection<Long>, now: Long): Map<Long, Long> {
    ensureLoaded()
    return denFeatureIds.associateWith { id ->
      if (lastHunted.putIfAbsent(id, now) == null) dirty += id
      now - lastHunted.getValue(id)
    }
  }

  @Scheduled(fixedDelay = FLUSH_EVERY_MS, initialDelay = FLUSH_EVERY_MS)
  @Transactional
  fun flush() {
    if (dirty.isEmpty()) return
    val ids = dirty.toList()
    dirty.removeAll(ids.toSet())
    repository.saveAll(ids.mapNotNull { id -> lastHunted[id]?.let { HuntedDen(id, it) } })
  }

  override fun wipe() {
    repository.deleteAll()
    lastHunted.clear()
    dirty.clear()
  }

  private fun ensureLoaded() {
    if (loaded) return
    synchronized(this) {
      if (loaded) return
      repository.findAll().forEach { lastHunted.merge(it.denFeatureId, it.lastHuntedSecond, ::maxOf) }
      loaded = true
    }
  }

  private companion object {
    const val FLUSH_EVERY_MS = 5 * 60 * 1000L
  }
}
