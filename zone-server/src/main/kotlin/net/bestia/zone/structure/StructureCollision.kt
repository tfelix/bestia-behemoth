package net.bestia.zone.structure

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.VoxelChunk
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Service

/**
 * Makes standing building pieces block movement and hold each other up: keeps their cells and their stability, and
 * rebuilds the walk tables they touch.
 *
 * Tick thread only, like [ChunkService].
 */
@Service
class StructureCollision(
  private val chunkService: ChunkService,
  private val catalogue: StructurePieceCatalogue,
  private val aoi: EntityAOIService
) {

  /** Built on first use: the world's chunk shape is only known once it has loaded. */
  private val index by lazy { StructureCellIndex(chunkService.config, chunkService::normalise) }

  private val support = StructureSupport()

  /** A piece to put back at boot. */
  data class Standing(val pieceId: Long, val kind: StaticEntityKind, val origin: Vec3L, val yaw: Float)

  @PostConstruct
  fun register() {
    chunkService.useStructures { chunk -> index.cellsOf(chunk) }
  }

  fun isPiece(kind: StaticEntityKind): Boolean {
    return catalogue.isPiece(kind)
  }

  /**
   * Whether a piece fits at [origin]: none of its cells is taken by another piece or by terrain, nobody stands where
   * it would fill a cell, and it is held up by the ground or by pieces that are.
   */
  fun fits(kind: StaticEntityKind, origin: Vec3L, yaw: Float): Boolean {
    val piece = catalogue.of(kind)
    val cells = cellsOf(piece, origin, yaw)
    val terrain = TerrainProbe()

    val isFree = !index.isTaken(cells) && cells.none { it.fill != Occupancy.EMPTY && terrain.isSolid(it.x, it.y, it.z) }
    if (!isFree || cells.any(::isOccupied)) return false

    val stability = support.stabilityIfPlaced(piece.material, cells, isGrounded(cells, terrain))
    return stability >= piece.material.minStability
  }

  /** Puts a standing piece's cells in. */
  fun stand(pieceId: Long, kind: StaticEntityKind, origin: Vec3L, yaw: Float) {
    val piece = catalogue.of(kind)
    val cells = cellsOf(piece, origin, yaw)

    support.add(pieceId, piece.material, cells, isGrounded(cells, TerrainProbe()))
    chunkService.structuresChanged(index.put(pieceId, cells))
  }

  /** Puts back every stored piece at once, so their stability is settled once rather than once per piece. */
  fun standAll(standing: List<Standing>) {
    val terrain = TerrainProbe()
    val placed = standing.map {
      val piece = catalogue.of(it.kind)
      val cells = cellsOf(piece, it.origin, it.yaw)
      chunkService.structuresChanged(index.put(it.pieceId, cells))

      StructureSupport.PlacedPiece(it.pieceId, piece.material, cells, isGrounded(cells, terrain))
    }

    support.addAll(placed)
  }

  /** Takes a piece's cells out. Nothing happens for a piece that never stood. */
  fun fall(pieceId: Long) {
    val unsupported = support.remove(pieceId)
    chunkService.structuresChanged(index.remove(pieceId))

    // Collapsing them is the destruction slice's job; until then they stand on.
    if (unsupported.isNotEmpty()) LOG.debug { "Piece $pieceId fell and left $unsupported without support" }
  }

  /** A piece placed at a yaw stands at the nearest quarter turn: pieces snap to the grid. */
  private fun cellsOf(piece: StructurePiece, origin: Vec3L, yaw: Float): List<StructureCell> {
    return StructureRasterizer.rasterize(piece, origin, StructureRasterizer.quarterTurnsOf(yaw))
  }

  private fun isGrounded(cells: List<StructureCell>, terrain: TerrainProbe): Boolean {
    return cells.any { terrain.isSolid(it.x, it.y, it.z - 1) }
  }

  /** Someone whose body, standing at z and one voxel above it, is where the cell would be filled. */
  private fun isOccupied(cell: StructureCell): Boolean {
    if (cell.fill == Occupancy.EMPTY) return false

    var occupied = false
    aoi.forEachInBox(cell.x, cell.y, cell.z - 1, cell.x, cell.y, cell.z, setOf(AoiLayer.DYNAMIC)) { _, _, _, _ ->
      occupied = true
    }

    return occupied
  }

  /** Reads terrain voxels, merging each chunk once however many cells ask about it. */
  private inner class TerrainProbe {

    private val merged = HashMap<ChunkPos, VoxelChunk>()

    fun isSolid(x: Long, y: Long, z: Long): Boolean {
      val at = ChunkCoords.localise(chunkService.config, x, y, z) ?: return false
      val chunk = merged.getOrPut(at.chunk) { chunkService.merged(chunkService.normalise(at.chunk)) }

      return chunk[at.localX, at.localY, at.localZ].solid
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
