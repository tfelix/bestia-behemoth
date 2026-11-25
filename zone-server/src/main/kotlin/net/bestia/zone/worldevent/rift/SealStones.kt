package net.bestia.zone.worldevent.rift

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PropKindRegistry
import net.bestia.zone.prop.WorldObjectSite
import net.bestia.zone.prop.WorldObjectSource
import net.bestia.zone.world.WorldService
import net.bestia.zone.worldevent.persistence.StoredSealStone
import net.bestia.zone.worldevent.persistence.StoredSealStoneRepository
import org.springframework.stereotype.Component

/**
 * The seal stones sealed rifts left, each holding the mana its rift gathered, served per chunk like the trees. They
 * cannot be broken yet; an old seal weakening is a later event. Tick-thread only, loaded at boot.
 */
@Component
class SealStones(
  private val repository: StoredSealStoneRepository,
  private val propKinds: PropKindRegistry,
  private val worldService: WorldService,
  private val asyncJobExecutor: AsyncJobExecutor,
) : WorldObjectSource {

  private val stones = ArrayList<StoredSealStone>()

  override val kinds: Set<StaticEntityKind> = setOf(StaticEntityKind.SEAL_STONE)

  override fun sitesIn(chunk: ChunkPos): List<WorldObjectSite> {
    val size = worldService.config.chunkSize.toLong()
    return stones
      .filter { Math.floorDiv(it.x, size).toInt() == chunk.x && Math.floorDiv(it.y, size).toInt() == chunk.y }
      .map(::siteOf)
  }

  /** Raises a stone holding [motes] at [at] and writes it off the tick. */
  fun raise(at: Vec3L, motes: Double): WorldObjectSite {
    val stone = StoredSealStone(
      at.x, at.y, at.z, motes, worldService.record.shapeVersion, worldService.record.pipelineVersion,
    )
    stones += stone
    asyncJobExecutor.submit(JOB_KEY) { repository.save(stone) }
    return siteOf(stone)
  }

  /** Boot only. Deletes the stones of another world. */
  fun loadAll() {
    val (current, stale) = repository.findAll().partition {
      it.worldShapeVersion == worldService.record.shapeVersion && it.pipelineVersion == worldService.record.pipelineVersion
    }
    if (stale.isNotEmpty()) repository.deleteAll(stale)
    stones += current
    LOG.info { "Loaded ${stones.size} seal stone(s)" }
  }

  private fun siteOf(stone: StoredSealStone) = WorldObjectSite(
    kind = StaticEntityKind.SEAL_STONE,
    propId = 0,
    position = Vec3L(stone.x, stone.y, stone.z),
    variant = 0,
    heightDm = (propKinds.of(StaticEntityKind.SEAL_STONE).collider.height * 10).toInt(),
    yaw = 0f,
  )

  private companion object {
    val LOG = KotlinLogging.logger { }
    const val JOB_KEY = "seal-stones"
  }
}
