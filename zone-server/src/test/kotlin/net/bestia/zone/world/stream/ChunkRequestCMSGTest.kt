package net.bestia.zone.world.stream

import net.bestia.bnet.proto.ChunkRequestCMSGProto
import net.bestia.worldgen.core.ChunkPos
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ChunkRequestCMSGTest {

  /** The largest view volume is 11 x 11 x 3 chunks; every entry beyond that is work for the tick thread. */
  @Test
  fun `a request carries at most a bounded number of chunks`() {
    val proto = ChunkRequestCMSGProto.ChunkRequestCMSG.newBuilder()
      .addAllChunks((0 until 5000).map { ChunkCoords.toProto(ChunkPos(it, 0)) })
      .build()

    assertTrue(ChunkRequestCMSG.fromBnet(1L, proto).chunks.size <= 1024)
  }
}
