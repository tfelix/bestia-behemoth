package net.bestia.zone.worldevent.rift

import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.spawn.ecs.WildSpawnerService
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.SettlementLoreService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaTier
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import org.springframework.stereotype.Component
import kotlin.math.hypot
import kotlin.random.Random

/**
 * What comes out of an open rift. Every 20 to 30 minutes a wave of the province's own bestia, stronger with each
 * stage and touched by the mana, makes for the nearest town; fewer come while nobody watches the gate. At stage 3
 * the anchor steps out, and while it stands the rift cannot be sealed. When the rift ends, its creatures go with it.
 *
 * On the tick, in the rift system's deferred block.
 */
@Component
class RiftHordes(
  private val config: RiftConfig,
  private val provinces: ProvinceService,
  private val dens: WildSpawnerService,
  private val bestiaCatalogue: BestiaCatalogue,
  private val spawner: BestiaEntitySpawner,
  private val chunks: ChunkService,
  private val worldService: WorldService,
  private val deletionQueue: PersistedEntityDeletionQueue,
  private val clock: BestiaClock,
) {

  /** Wave bestia by rift. Not persisted: they do not outlive a restart, the rift sends more. */
  private val waves = HashMap<Long, MutableList<EntityId>>()

  fun tend(world: World, rifts: List<StoredRift>, now: Long) {
    rifts.forEach { rift ->
      if (rift.phase == Phase.OPEN) {
        noteAnchorFall(world, rift)
        if (rift.stage >= rift.nextAnchorStage && rift.anchorEntityId == null && !rift.anchorSlain) callAnchor(world, rift)
        if (now >= rift.nextWaveAtSecond) sendWave(world, rift, now)
      } else {
        clear(world, rift)
      }
    }
  }

  private fun noteAnchorFall(world: World, rift: StoredRift) {
    val anchor = rift.anchorEntityId ?: return
    if (world.isAlive(anchor)) return
    rift.anchorEntityId = null
    rift.anchorSlain = true
  }

  private fun callAnchor(world: World, rift: StoredRift) {
    val at = gateGround(rift) ?: return
    val warden = bestiaCatalogue.byIdentifier(ANCHOR)
    val level = kinOf(rift).maxOfOrNull { it.levelMax }?.plus(config.anchorLevelsAboveDens) ?: warden.levelMax

    rift.anchorEntityId = spawner.spawnMob(world, warden.id, at, homePosition = at, level = level)
      .also { world.add(it, EventCreature(RIFT, rift.id)) }
  }

  private fun sendWave(world: World, rift: StoredRift, now: Long) {
    val random = Random(rift.id * 31 + rift.nextWaveAtSecond)
    rift.nextWaveAtSecond = now + waveGap(random)

    val kin = kinOf(rift)
    val from = gateGround(rift) ?: return
    if (kin.isEmpty()) return
    val town = nearestTown(rift) ?: from

    val size = if (isWatched(world, rift)) config.waveSizeWatched else config.waveSizeUnwatched
    val members = waves.getOrPut(rift.id) { ArrayList() }
    repeat(size) {
      val species = kin[random.nextInt(kin.size)]
      members += spawner.spawnMob(
        world, species.id, from,
        persistent = false,
        homePosition = town,
        level = species.levelMax + config.waveLevelsPerStage * rift.stage,
        manaTier = ManaTier.OMINOUS,
      ).also { world.add(it, EventCreature(RIFT, rift.id)) }
    }
  }

  private fun clear(world: World, rift: StoredRift) {
    waves.remove(rift.id)?.forEach { if (world.isAlive(it)) world.destroy(it) }
    rift.anchorEntityId?.let { anchor ->
      deletionQueue.enqueue(anchor)
      if (world.isAlive(anchor)) world.destroy(anchor)
      rift.anchorEntityId = null
    }
  }

  /** The species the province's dens breed, never a boss or an event creature. */
  private fun kinOf(rift: StoredRift): List<Bestia> =
    dens.dens
      .filter { provinces.provinceAt(it.position.x.toDouble(), it.position.y.toDouble())?.index == rift.provinceIndex }
      .mapNotNull { bestiaCatalogue.find(it.bestiaId) }
      .filterNot { it.boss || it.worldBoss || it.eventOnly }
      .distinctBy { it.id }

  private fun isWatched(world: World, rift: StoredRift): Boolean {
    var watched = false
    world.query(Master::class, Position::class).each { _ ->
      val at = get<Position>()
      if (hypot(at.x - rift.x, at.y - rift.y) <= config.watchRadiusMetres) watched = true
    }
    return watched
  }

  private fun nearestTown(rift: StoredRift): Vec3L? =
    SettlementLoreService.settlementPositions(worldService.generated).values
      .minByOrNull { hypot(it.x - rift.x, it.y - rift.y) }
      ?.let { grounded(it.x, it.y) }

  private fun waveGap(random: Random): Long {
    val minutes = config.waveMinRealMinutes + random.nextDouble() * (config.waveMaxRealMinutes - config.waveMinRealMinutes)
    return (minutes * 60 * clock.speedFactor).toLong()
  }

  private fun gateGround(rift: StoredRift): Vec3L? = grounded(rift.x, rift.y)

  private fun grounded(x: Double, y: Double): Vec3L? {
    val config = worldService.config
    val voxelX = Math.floor(x / config.voxelSize).toLong()
    val voxelY = Math.floor(y / config.voxelSize).toLong()
    val elevation = chunks.surfaceElevationAt(voxelX, voxelY) ?: return null
    return Vec3L(voxelX, voxelY, ChunkCoords.standingZ(config, elevation))
  }

  private companion object {
    const val ANCHOR = "rift_warden"

    /** The rift event's name, which its creatures carry. */
    const val RIFT = "Rift"
  }
}
