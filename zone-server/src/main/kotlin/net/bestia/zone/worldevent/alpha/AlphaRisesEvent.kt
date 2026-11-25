package net.bestia.zone.worldevent.alpha

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourService
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.EventGround
import net.bestia.zone.worldevent.ProvinceSignals
import net.bestia.zone.worldevent.WorldEvent
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import org.springframework.stereotype.Component

/**
 * A pack nobody hunts grows an alpha: the den's own species, a few levels above its strongest, that prowls the road
 * or the fields near town and attacks what passes. Its tracks show on the road like any creature's. The den left
 * alone longest in the province rises first; killing the alpha ends the event.
 */
@Component
class AlphaRisesEvent(
  private val alphaDens: AlphaDens,
  private val hunting: DenHunting,
  private val provinces: ProvinceService,
  private val ground: EventGround,
  private val clock: BestiaClock,
  private val spawner: BestiaEntitySpawner,
  private val rumours: RumourService,
  private val deletionQueue: PersistedEntityDeletionQueue,
  private val world: WorldView,
) : WorldEvent {

  override fun fits(signals: ProvinceSignals): Boolean = longestUnhuntedIn(signals) != null

  override fun start(signals: ProvinceSignals): WorldEvent.Started? {
    val alphaDen = longestUnhuntedIn(signals) ?: return null
    val from = ground.grounded(alphaDen.at.x, alphaDen.at.y) ?: return null
    val prowl = ground.grounded(alphaDen.prowl.x, alphaDen.prowl.y) ?: return null

    val id = world.read {
      spawner.spawnMob(this, alphaDen.species.id, from, homePosition = prowl, level = alphaDen.species.levelMax + ALPHA_LEVELS)
        .also { add(it, EventCreature(script, it)) }
    }
    // The pack's surplus went into the alpha; the den starts counting again.
    hunting.hunted(alphaDen.den.identity.featureId, clock.now().absoluteSecond)
    rumours.post(RumourKind.ALPHA_PROWLS, prowl.x, prowl.y, RUMOUR_STRENGTH)

    return WorldEvent.Started(from, id)
  }

  override fun isDefeated(instance: WorldEventInstance): Boolean {
    val id = instance.subjectId ?: return true
    return world.read { !isAlive(id) }
  }

  override fun heldArea(instance: WorldEventInstance): WorldEvent.HeldArea? {
    val id = instance.subjectId ?: return null
    val at = world.read { get(id, Position::class)?.toVec3L() } ?: return null
    return WorldEvent.HeldArea(at.x.toDouble(), at.y.toDouble(), HELD_RADIUS_METRES)
  }

  override fun end(instance: WorldEventInstance) {
    val id = instance.subjectId ?: return
    world.read {
      deletionQueue.enqueue(id)
      if (isAlive(id)) destroy(id)
    }
  }

  private fun longestUnhuntedIn(signals: ProvinceSignals): AlphaDens.AlphaDen? {
    val inProvince = alphaDens.all.filter { provinces.provinceAt(it.at.x, it.at.y)?.index == signals.province.index }
    if (inProvince.isEmpty()) return null

    val unhunted = hunting.unhuntedSeconds(inProvince.map { it.den.identity.featureId }, clock.now().absoluteSecond)
    return inProvince
      .filter { unhunted.getValue(it.den.identity.featureId) >= UNHUNTED_SECONDS }
      .maxByOrNull { unhunted.getValue(it.den.identity.featureId) }
  }

  private companion object {
    /** A week of Bestia days, a little over two real days: long enough that a busy hunting ground never grows one. */
    const val UNHUNTED_SECONDS = 7 * 24 * 3600L

    /** Above its kin's highest level, like the apex the mana draws. */
    const val ALPHA_LEVELS = 8

    const val HELD_RADIUS_METRES = 200.0

    /** Heard in the next towns: torn sheep are everyone's business. */
    const val RUMOUR_STRENGTH = 0.6
  }
}
