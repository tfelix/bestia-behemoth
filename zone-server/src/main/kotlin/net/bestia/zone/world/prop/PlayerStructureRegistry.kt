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
  val yaw: Float,

  /** See [PlayerStructure.totalBuildSeconds]: 0 for something already standing. */
  val totalBuildSeconds: Float = 0f,
  val remainingBuildSeconds: Float = 0f
) {

  val isUnderConstruction: Boolean
    get() {
      return totalBuildSeconds > 0f
    }
}

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

  /** Which column each entry was indexed under, so an update can find its list without scanning them all. */
  private val columnOf = HashMap<Long, Long>()

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
    chunkY: Int,

    /** Non-zero to record a construction site rather than a finished structure. */
    buildSeconds: Float = 0f
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
        chunkY = chunkY,
        totalBuildSeconds = buildSeconds,
        remainingBuildSeconds = buildSeconds
      )
    )

    val entry = StructureEntry(saved.id, kind, ownerMasterId, position, yaw, buildSeconds, buildSeconds)
    index(chunkX, chunkY, entry)

    return entry
  }

  /**
   * Records how much work a site still owes, so a restart resumes rather than restarts it.
   *
   * Called on a cadence rather than per tick - see [ConstructionSystem]. The in-memory entry is replaced
   * synchronously because [PlayerStructureSource] reads it in the same tick a site may finish in.
   */
  fun updateProgress(structureId: Long, remainingSeconds: Float) {
    val current = byId[structureId] ?: return
    reindex(current.copy(remainingBuildSeconds = remainingSeconds))

    asyncJobExecutor.submit(structureId) {
      repository.findById(structureId).ifPresent { row ->
        row.remainingBuildSeconds = remainingSeconds
        repository.save(row)
      }
    }
  }

  /**
   * Turns a site into a standing structure. Idempotent, and a no-op for one that was never a site.
   *
   * [settledPosition] is where the site actually ended up, which is not always where it was placed: a site is
   * an ordinary entity, so `ChunkStreamSystem.groundNewcomers` snaps the z the client guessed. Writing it back
   * is what keeps the finished prop from standing at the guess.
   */
  fun finish(structureId: Long, settledPosition: Vec3L) {
    val current = byId[structureId] ?: return
    if (!current.isUnderConstruction) return

    reindex(current.copy(position = settledPosition, totalBuildSeconds = 0f, remainingBuildSeconds = 0f))

    asyncJobExecutor.submit(structureId) {
      repository.findById(structureId).ifPresent { row ->
        row.z = settledPosition.z
        row.totalBuildSeconds = 0f
        row.remainingBuildSeconds = 0f
        repository.save(row)
      }
    }
  }

  /** Every site still owing work, for the boot path that puts them back into the world. */
  fun underConstruction(): List<StructureEntry> {
    return byId.values.filter { it.isUnderConstruction }
  }

  /**
   * Forgets a structure and deletes its row. No-op for an id that is not indexed, which is what makes a
   * double demolition harmless.
   */
  fun remove(structureId: Long, chunkX: Int, chunkY: Int) {
    val column = pack(chunkX, chunkY)
    byColumn[column]?.removeIf { it.id == structureId }
    if (byColumn[column]?.isEmpty() == true) byColumn.remove(column)

    columnOf.remove(structureId)
    if (byId.remove(structureId) == null) return

    asyncJobExecutor.submit(structureId) { repository.deleteById(structureId) }
  }

  /** Boot-time only, before the tick loop starts - see the class note. */
  fun loadAll() {
    repository.findAll().forEach { row ->
      val entry = StructureEntry(
        row.id, row.kind, row.ownerMasterId, Vec3L(row.x, row.y, row.z), row.yaw,
        row.totalBuildSeconds, row.remainingBuildSeconds
      )
      index(row.chunkX, row.chunkY, entry)
    }

    LOG.info { "Loaded ${byId.size} player structure(s) across ${byColumn.size} chunk column(s)" }
  }

  /** Swaps an entry for an updated copy of itself, in both maps, leaving its column unchanged. */
  private fun reindex(entry: StructureEntry) {
    byId[entry.id] = entry

    val column = columnOf[entry.id] ?: return
    byColumn[column]?.replaceAll { if (it.id == entry.id) entry else it }
  }

  private fun index(chunkX: Int, chunkY: Int, entry: StructureEntry) {
    val column = pack(chunkX, chunkY)
    byColumn.getOrPut(column) { mutableListOf() }.add(entry)
    byId[entry.id] = entry
    columnOf[entry.id] = column
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    fun pack(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
  }
}
