package net.bestia.zone.worldevent.rare

import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.townsfolk.knowledge.Knowledge
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourService
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.ProvinceSignals
import net.bestia.zone.worldevent.WorldEvent
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import org.springframework.stereotype.Component

/**
 * A named bestia comes down where its land lies in the province while its sky holds: a storm at dusk over a marsh,
 * a clear night of the full moon in an old forest. It stays near that spot and leaves when the sky moves on, so a
 * hunter who reads the forecast and the hints can be there in time. Killing it ends the event.
 */
@Component
class RareBestiaEvent(
  private val rares: RareBestiaCatalogue,
  private val ground: RareGround,
  private val clock: BestiaClock,
  private val bestiaCatalogue: BestiaCatalogue,
  private val spawner: BestiaEntitySpawner,
  private val rumours: RumourService,
  private val deletionQueue: PersistedEntityDeletionQueue,
  private val world: WorldView,
) : WorldEvent {

  /** A rare whose land and sky fit at [at] now. */
  private data class Sighting(val rare: RareBestia, val at: Vec3L)

  override fun fits(signals: ProvinceSignals): Boolean = sightingsIn(signals).isNotEmpty()

  override fun start(signals: ProvinceSignals): WorldEvent.Started? {
    val sighting = sightingsIn(signals).randomOrNull() ?: return null
    val species = bestiaCatalogue.byIdentifier(sighting.rare.bestia)

    val id = world.read {
      spawner.spawnMob(this, species.id, sighting.at, homePosition = sighting.at)
        .also { add(it, EventCreature(script, it)) }
    }
    rumours.post(
      RumourKind.RARE_BESTIA_SEEN, sighting.at.x, sighting.at.y, SEEN_STRENGTH,
      mapOf(RARE_SLOT to Knowledge.Slot.Token(sighting.rare.nameKey)),
    )

    return WorldEvent.Started(sighting.at, id)
  }

  override fun isDefeated(instance: WorldEventInstance): Boolean {
    val id = instance.subjectId ?: return true
    return world.read { !isAlive(id) }
  }

  /** Its sky moved on where it came down. */
  override fun isOver(instance: WorldEventInstance): Boolean {
    val rare = rareOf(instance) ?: return true
    return !rare.fitsSky(ground.weatherAt(Vec3L(instance.x, instance.y, instance.z)), clock.now())
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

  /** Cheapest test first: the time is the same everywhere, the biome is a lookup, the weather needs the ground. */
  private fun sightingsIn(signals: ProvinceSignals): List<Sighting> {
    val now = clock.now()
    val awake = rares.all().filter { it.fitsTime(now) }
    if (awake.isEmpty()) return emptyList()

    return ground.spotsOf(signals.province.regions).flatMap { spot ->
      val livingHere = awake.filter { it.livesOn(spot.biome) }
      if (livingHere.isEmpty()) return@flatMap emptyList()

      val at = ground.grounded(spot) ?: return@flatMap emptyList()
      val kind = ground.weatherAt(at)
      livingHere.filter { it.fitsSky(kind, now) }.map { Sighting(it, at) }
    }
  }

  private fun rareOf(instance: WorldEventInstance): RareBestia? {
    val id = instance.subjectId ?: return null
    val speciesId = world.read { get(id, EntityVisual::class)?.id } ?: return null
    val species = bestiaCatalogue.find(speciesId) ?: return null
    return rares.ofSpecies(species.identifier)
  }

  private companion object {
    const val HELD_RADIUS_METRES = 150.0

    /** Heard a little way off: a sighting is news for the next village, not the province. */
    const val SEEN_STRENGTH = 0.4

    const val RARE_SLOT = "rare"
  }
}
