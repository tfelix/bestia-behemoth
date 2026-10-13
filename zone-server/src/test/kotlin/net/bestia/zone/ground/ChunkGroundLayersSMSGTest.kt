package net.bestia.zone.ground

import net.bestia.bnet.proto.ChunkGroundLayersSMSGProto
import net.bestia.bnet.proto.ChunkGroundLayersSMSGProto.ChunkGroundLayerEncoding
import net.bestia.bnet.proto.ChunkGroundLayersSMSGProto.GroundLayerId
import net.bestia.worldgen.core.ChunkPos
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * What goes on the wire for one column's layers.
 *
 * Any byte string is a legal payload, so a disagreement about layout draws a plausible pattern in the wrong
 * places instead of failing; the per-layer fields are what a client decodes by, and they are asserted here
 * against the proto's own types rather than against this class's.
 */
class ChunkGroundLayersSMSGTest {

  private val chunk = ChunkPos(3, -4, 0)

  private val sparse = byteArrayOf(0x00, 0x00, 0x01, 0x21, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0x03, 0x10)

  private fun worn(lastDecayedSecond: Long = 4_000): GroundLayerEntry {
    return GroundLayerEntry(GroundLayer.WORN, GroundLayerEncoding.LEVELS_SPARSE_V1, sparse, lastDecayedSecond)
  }

  private fun scorched(): GroundLayerEntry {
    return GroundLayerEntry(GroundLayer.SCORCHED, GroundLayerEncoding.NIBBLE_V1, byteArrayOf(0x12, 0x34))
  }

  private fun wire(vararg layers: GroundLayerEntry): ChunkGroundLayersSMSGProto.ChunkGroundLayersSMSG {
    return ChunkGroundLayersSMSG(chunk, layers.toList()).toBnetEnvelope().chunkGroundLayers
  }

  @Test
  fun `the column is addressed by chunk`() {
    val message = wire(worn())

    assertEquals(3, message.pos.x)
    assertEquals(-4, message.pos.y)
  }

  @Test
  fun `a sparse layer carries its exact bytes and the clock they have faded to`() {
    val entry = wire(worn(lastDecayedSecond = 129_091)).layersList.single()

    assertEquals(GroundLayerId.GROUND_LAYER_WORN, entry.layer)
    assertEquals(ChunkGroundLayerEncoding.CHUNK_GROUND_LAYER_ENCODING_LEVELS_SPARSE_V1, entry.encoding)
    assertContentEquals(sparse, entry.cells.toByteArray())
    assertEquals(129_091L, entry.lastDecayedSecond)
  }

  @Test
  fun `a scar stays a nibble layer with no clock`() {
    val entry = wire(scorched()).layersList.single()

    assertEquals(GroundLayerId.GROUND_LAYER_SCORCHED, entry.layer)
    assertEquals(ChunkGroundLayerEncoding.CHUNK_GROUND_LAYER_ENCODING_NIBBLE_V1, entry.encoding)
    assertContentEquals(byteArrayOf(0x12, 0x34), entry.cells.toByteArray())
    assertEquals(0L, entry.lastDecayedSecond)
  }

  /** The client replaces exactly the layers a message names, so one that is not named must not appear. */
  @Test
  fun `only the layers it was given are in the message`() {
    val message = wire(scorched())

    assertEquals(listOf(GroundLayerId.GROUND_LAYER_SCORCHED), message.layersList.map { it.layer })
  }

  @Test
  fun `layers are written in layer order whatever order they were gathered in`() {
    val bloodied = GroundLayerEntry(GroundLayer.BLOODIED, GroundLayerEncoding.LEVELS_SPARSE_V1, sparse, 1)

    val message = wire(bloodied, worn(), scorched())

    assertEquals(
      listOf(GroundLayerId.GROUND_LAYER_SCORCHED, GroundLayerId.GROUND_LAYER_WORN, GroundLayerId.GROUND_LAYER_BLOODIED),
      message.layersList.map { it.layer }
    )
  }

  /** Empty cells retire the layer; the encoding is still the layer's own so no decoder meets one it refuses. */
  @Test
  fun `a retired layer is an entry with no cells`() {
    val retired = GroundLayerEntry.retired(GroundLayer.WORN, GroundLayerEncoding.LEVELS_SPARSE_V1)

    val entry = wire(retired).layersList.single()

    assertEquals(GroundLayerId.GROUND_LAYER_WORN, entry.layer)
    assertEquals(ChunkGroundLayerEncoding.CHUNK_GROUND_LAYER_ENCODING_LEVELS_SPARSE_V1, entry.encoding)
    assertTrue(entry.cells.isEmpty)
    assertTrue(retired.isEmpty)
  }

  @Test
  fun `two messages with the same bytes are equal`() {
    val a = ChunkGroundLayersSMSG(chunk, listOf(worn(), scorched()))
    val b = ChunkGroundLayersSMSG(chunk, listOf(worn(), scorched()))

    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
  }

  @Test
  fun `a different clock is a different message`() {
    assertNotEquals(
      ChunkGroundLayersSMSG(chunk, listOf(worn(lastDecayedSecond = 1))),
      ChunkGroundLayersSMSG(chunk, listOf(worn(lastDecayedSecond = 2)))
    )
  }

  /** Protobuf's own `toString` would escape every byte of every layer into the log at TRACE. */
  @Test
  fun `it logs as a size rather than as its bytes`() {
    val text = ChunkGroundLayersSMSG(chunk, listOf(worn(), scorched())).toString()

    assertTrue("2 layer(s)" in text, text)
    assertTrue("11B" in text, text)
    assertFalse("cells" in text, text)
  }
}
