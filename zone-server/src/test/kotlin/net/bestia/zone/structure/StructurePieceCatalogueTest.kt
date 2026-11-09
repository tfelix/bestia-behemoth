package net.bestia.zone.structure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The client predicts steps with its own copy of the pieces and of [StructureRasterizer]. Both sides compare
 * against one golden file, so the copies cannot drift apart unseen. Run with `UPDATE_GOLDEN=1` to rewrite it.
 */
class StructurePieceCatalogueTest {

  private val catalogue = StructurePieceCatalogue().apply { load() }

  @Test
  fun `every building piece is described`() {
    for (kind in listOf(StaticEntityKind.STRUCTURE_WALL, StaticEntityKind.STRUCTURE_FLOOR, StaticEntityKind.STRUCTURE_STAIRS)) {
      assertTrue(catalogue.isPiece(kind), "$kind")
    }
  }

  @Test
  fun `rasterized pieces match the file the client checks against`() {
    val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    val rasterized = mapper.writeValueAsString(rasterizeAll()) + "\n"

    if (System.getenv("UPDATE_GOLDEN") != null) GOLDEN.writeText(rasterized)

    assertEquals(GOLDEN.readText(), rasterized, "run with UPDATE_GOLDEN=1, then fix the client's copy to match")
  }

  /** Every piece at the origin in all four turns, as `[x, y, z, fill, edges]` rows in a stable order. */
  private fun rasterizeAll(): Map<String, List<List<List<Long>>>> {
    return PIECES.associate { kind ->
      kind.name to (0..3).map { turns ->
        StructureRasterizer.rasterize(catalogue.of(kind), Vec3L(0, 0, 0), turns)
          .map { listOf(it.x, it.y, it.z, it.fill.toLong(), it.edges.toLong()) }
          .sortedWith(compareBy({ it[0] }, { it[1] }, { it[2] }, { it[4] }))
      }
    }
  }

  private companion object {
    val GOLDEN = File("src/test/resources/structure-cells.golden.json")

    val PIECES = listOf(StaticEntityKind.STRUCTURE_WALL, StaticEntityKind.STRUCTURE_FLOOR, StaticEntityKind.STRUCTURE_STAIRS)
  }
}
