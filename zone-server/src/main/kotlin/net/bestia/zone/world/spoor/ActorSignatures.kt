package net.bestia.zone.world.spoor

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * What each walking thing is, kept long enough to answer for the tracks it left.
 *
 * ### Why this outlives the entity
 *
 * Following something that has walked out of sight is the whole point of tracking, and an entity that has
 * walked out of sight may have despawned, died or logged out. Asking the world what made a print is therefore
 * the one question that cannot be asked when the print is read - so the answer is taken while the thing is
 * still walking and kept here, on its own clock.
 *
 * ### Tick-thread only
 *
 * Written by [ActorSignatureSystem] and read through `SkillWorld.readTracks`, which opens a world scope for
 * exactly this reason - a skill resolves on a background worker. The convention `GroundWearRegistry` documents
 * for its own map.
 */
@Service
class ActorSignatures(
  private val config: SpoorConfig,
) {

  private class Held(val signature: ActorSignature, val takenSecond: Long)

  private val byEntity = HashMap<EntityId, Held>()

  private var warnedFull = false

  val size get() = byEntity.size

  /**
   * Whether this actor's description is missing or stale enough to be worth rebuilding.
   *
   * The cheap test that keeps the per-tick pass to one map lookup per walking creature: building a signature
   * reads several components and a catalogue row, and almost nothing about a creature changes while it walks.
   */
  fun needsRefresh(entityId: EntityId, nowSecond: Long): Boolean {
    val held = byEntity[entityId] ?: return true

    return nowSecond - held.takenSecond >= config.refreshSeconds
  }

  fun remember(entityId: EntityId, signature: ActorSignature, nowSecond: Long) {
    if (!byEntity.containsKey(entityId) && byEntity.size >= config.maxSignatures) {
      if (!warnedFull) {
        warnedFull = true
        LOG.warn { "spoor is holding ${byEntity.size} signatures, the configured maximum; tracks laid now will not be readable" }
      }
      return
    }

    warnedFull = false
    byEntity[entityId] = Held(signature, nowSecond)
  }

  /** What [entityId] was, or null when nothing was recorded or it has been forgotten. */
  fun of(entityId: EntityId): ActorSignature? {
    return byEntity[entityId]?.signature
  }

  /**
   * Forgets actors nobody has seen walking for [SpoorConfig.signatureTtlSeconds].
   *
   * Keyed on when the description was taken rather than on the entity still existing, deliberately: an entity
   * that died an hour ago is exactly the one a tracker is asking about.
   */
  fun sweep(nowSecond: Long): Int {
    val before = byEntity.size
    byEntity.values.removeIf { nowSecond - it.takenSecond >= config.signatureTtlSeconds }

    return before - byEntity.size
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
