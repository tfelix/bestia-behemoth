package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.entity.GrowthStage
import net.bestia.zone.entity.StaticEntityKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The growth stage travels as an ordinal, so the Kotlin enum and the proto enum have to stay in one order. A
 * swap would draw a sapling where a stump should be, with nothing failing.
 */
class ChunkStaticEntitiesSMSGTest {

  @Test
  fun `every growth stage reaches the wire as the proto value of the same name`() {
    for (stage in GrowthStage.entries) {
      val entry = ChunkStaticEntitiesSMSG.Entry(
        entityId = 1L, kind = StaticEntityKind.TREE, variant = 0, localX = 0, localY = 0, z = 0,
        heightDm = 60, yawCentiradians = 0, growth = stage
      )

      val wire = ChunkStaticEntitiesSMSG(ChunkPos(0, 0, 0), listOf(entry)).toBnetEnvelope()
        .chunkStaticEntities.getEntries(0)

      assertEquals("GROWTH_STAGE_${stage.name}", wire.growth.name)
    }
  }
}
