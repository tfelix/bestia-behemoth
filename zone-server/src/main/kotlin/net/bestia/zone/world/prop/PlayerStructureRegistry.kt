package net.bestia.zone.world.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Service

/** In-memory mirror of one [PlayerStructure] row, for the tick thread to read without a DB hit. */
data class StructureEntry(
  val id: Long,
  val kind: StaticEntityKind,
  val ownerMasterId: Long,
  val position: Vec3L,
  val yaw: Float
)

/**
 * Which structures players have built, and where.
 *
 * ### Tick-thread only, in memory
 *
 * The same convention [WorldObjectDivergenceRegistry] documents, and for the same reason:
 * [WorldObjectResidencyService.materialise] runs on the tick thread and would otherwise do a database
 * query per chunk column - on a 128 km world, thousands of them per minute as players walk around, almost
 * all returning nothing. [loadAll] runs once at boot before the tick loop starts; every other method runs
 * on the tick thread, so a plain `HashMap` is correct rather than merely convenient.
 *
 * ### Durable writes without blocking the tick
 *
 * A placement or a demolition updates the map synchronously - so the next [in] sees it within the same tick -
 * and hands the row write to [AsyncJobExecutor] keyed on the structure id, whose per-key ordering means a
 * structure placed and knocked down in quick succession cannot have its writes land out of order.
 *
 * The one exception is [place], which writes *first*: an insert is the only way to learn the generated id,
 * and the id is what the caller needs to stamp onto the entity it is about to create.
 */
@Service
class PlayerStructureRegistry(
  private val repository: PlayerStructureRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  /** Packed `(x, y)` chunk column -> the structures standing in it. */
  private val byColumn = HashMap<Long, MutableList<StructureEntry>>()

  private val byId = HashMap<Long, StructureEntry>()

  val size get() = byId.size

  fun `in`(chunkX: Int, chunkY: Int): List<StructureEntry> = byColumn[pack(chunkX, chunkY)] ?: emptyList()

  fun of(structureId: Long): StructureEntry? = byId[structureId]

  /**
   * Persists a structure and indexes it, returning the entry the caller should stamp onto the world.
   *
   * Synchronous, unlike every other write here: the caller needs the generated id in the same breath, and a
   * structure whose id arrived a tick later would already be standing in the world unnamed.
   */
  fun place(
    kind: StaticEntityKind,
    ownerMasterId: Long,
    position: Vec3L,
    yaw: Float,
    chunkX: Int,
    chunkY: Int
  ): StructureEntry {
    val saved = repository.save(
      PlayerStructure(
        kind = kind,
        ownerMasterId = ownerMasterId,
        x = position.x,
        y = position.y,
        z = position.z,
        yaw = yaw,
        chunkX = chunkX,
        chunkY = chunkY
      )
    )

    val entry = StructureEntry(saved.id, kind, ownerMasterId, position, yaw)
    index(chunkX, chunkY, entry)

    return entry
  }

  /**
   * Forgets a structure and deletes its row. No-op for an id that is not indexed, which is what makes a
   * double demolition harmless.
   */
  fun remove(structureId: Long, chunkX: Int, chunkY: Int) {
    val column = pack(chunkX, chunkY)
    byColumn[column]?.removeIf { it.id == structureId }
    if (byColumn[column]?.isEmpty() == true) byColumn.remove(column)

    if (byId.remove(structureId) == null) return

    asyncJobExecutor.submit(structureId) { repository.deleteById(structureId) }
  }

  /** Boot-time only, before the tick loop starts - see the class note. */
  fun loadAll() {
    repository.findAll().forEach { row ->
      val entry = StructureEntry(row.id, row.kind, row.ownerMasterId, Vec3L(row.x, row.y, row.z), row.yaw)
      index(row.chunkX, row.chunkY, entry)
    }

    LOG.info { "Loaded ${byId.size} player structure(s) across ${byColumn.size} chunk column(s)" }
  }

  private fun index(chunkX: Int, chunkY: Int, entry: StructureEntry) {
    byColumn.getOrPut(pack(chunkX, chunkY)) { mutableListOf() }.add(entry)
    byId[entry.id] = entry
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    fun pack(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
  }
}
