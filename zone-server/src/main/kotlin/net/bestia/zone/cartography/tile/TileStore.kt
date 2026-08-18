package net.bestia.zone.cartography.tile

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val LOG = KotlinLogging.logger { }

/**
 * Encoded tiles on disk, under one directory per world identity.
 *
 * ### Why a directory tree and not a table
 *
 * This is the first durable derived output in the repository - `world/WorldService` regenerates rasters at boot
 * and `world/stream/ChunkService` keeps even baked chunks in a `MemoryBlobStore` - so the choice is open, and a
 * directory of PNGs wins on the one axis that matters most here: you can look at it. `mapBake` writes files you
 * can open in an image viewer, diff between two runs, serve with any static file server, or delete with `rm`.
 * A blob table gives none of that and buys nothing, because tiles are regenerable by construction and so are
 * exactly what a backup should not contain.
 *
 * The cost is a lot of small files at fine levels. That is bounded by policy rather than by format: coarse
 * levels are baked whole and fine levels only around settlements, where the plan style has anything to draw.
 *
 * ### Writes are atomic
 *
 * A tile is written to a temporary file and moved into place. Without that, a bake interrupted halfway - or two
 * workers racing on the same tile, which the render pool makes possible - can leave a truncated PNG that
 * decodes to a grey band, and a corrupt cache entry that looks like a tile is the one failure mode that would
 * be blamed on the renderer for weeks.
 */
class TileStore(private val root: File, val key: MapWorldKey) {

  private val base = File(root, key.value)

  fun read(tile: TileId): ByteArray? {
    val file = fileOf(tile)
    if (!file.isFile) return null

    return try {
      file.readBytes()
    } catch (e: Exception) {
      // A cache is allowed to fail; the caller re-renders. Losing the tile is not losing anything.
      LOG.warn(e) { "Could not read cached tile $tile, will re-render" }
      null
    }
  }

  fun write(tile: TileId, bytes: ByteArray) {
    val file = fileOf(tile)
    file.parentFile.mkdirs()

    val temporary = File(file.parentFile, "${file.name}.${Thread.currentThread().id}.tmp")
    try {
      temporary.writeBytes(bytes)
      Files.move(
        temporary.toPath(), file.toPath(),
        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
      )
    } catch (e: Exception) {
      LOG.warn(e) { "Could not cache tile $tile" }
      temporary.delete()
    }
  }

  fun has(tile: TileId): Boolean = fileOf(tile).isFile

  fun fileOf(tile: TileId): File = File(base, "${tile.path()}.png")

  val directory: File get() = base

  /** Tiles held, and the bytes they occupy. For a tool to print; walks the tree, so not for a request path. */
  fun measure(): Stats {
    var count = 0L
    var bytes = 0L
    base.walkTopDown().forEach {
      if (it.isFile && it.name.endsWith(".png")) {
        count++
        bytes += it.length()
      }
    }
    return Stats(count, bytes)
  }

  /**
   * Deletes cache directories for every world identity but this one.
   *
   * Called when a world is provisioned rather than on a timer: the moment a new key exists, every tile under
   * every other key is unreachable, and leaving them costs disk for no possible benefit. Deliberately keeps the
   * current key's directory even if it is stale in some way this type cannot see - deleting tiles that are
   * about to be asked for would turn a boot into a full re-bake.
   */
  fun evictOtherWorlds(): Int {
    val siblings = root.listFiles() ?: return 0
    var removed = 0

    for (sibling in siblings) {
      if (!sibling.isDirectory || sibling.name == key.value) continue
      if (sibling.deleteRecursively()) removed++ else LOG.warn { "Could not evict stale tiles at $sibling" }
    }

    if (removed > 0) LOG.info { "Evicted $removed stale map tile cache(s) from $root" }
    return removed
  }

  class Stats(val tiles: Long, val bytes: Long) {

    val meanTileBytes: Long get() = if (tiles == 0L) 0 else bytes / tiles

    override fun toString() =
      "%d tiles, %.1f MB, mean %d bytes".format(tiles, bytes / 1024.0 / 1024.0, meanTileBytes)
  }
}
