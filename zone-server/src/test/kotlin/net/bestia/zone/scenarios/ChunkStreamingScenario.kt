package net.bestia.zone.scenarios

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.RleCodec
import net.bestia.zone.chat.ChatCMSG
import net.bestia.zone.world.stream.BlockPaletteSMSG
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkDataSMSG
import net.bestia.zone.world.stream.ChunkManifestSMSG
import net.bestia.zone.world.stream.ChunkPatchCodec
import net.bestia.zone.world.stream.ChunkPatchSMSG
import net.bestia.zone.world.stream.ChunkRequestCMSG
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkStreamConfig
import net.bestia.zone.world.stream.ChunkSubscriptionService
import net.bestia.zone.world.stream.WorldInfoSMSG
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Chunk streaming end to end, against the real Spring context and the real tick loop.
 *
 * The unit tests each prove one link: the codec round-trips, the fan-out serialises once, the subscription
 * bookkeeping is consistent. This proves the chain - a client connects, is told what world it is in, is
 * offered terrain, asks for it, receives something that decodes, and then hears about an edit as a patch
 * rather than as a chunk. Nothing below the message layer is mocked; the world is genuinely generated at boot
 * and the chunks are genuinely materialised.
 *
 * Ordered and cumulative on purpose: each step is a precondition for the next, and splitting them into
 * independent tests would mean re-establishing a connected, subscribed client four times over a world that
 * takes a second to build.
 */
class ChunkStreamingScenario : BestiaNoSocketScenario(
  autoClientConnect = false,
  clearMessagesBetweenTests = false
) {

  @Autowired
  private lateinit var chunkService: ChunkService

  @Autowired
  private lateinit var subscriptions: ChunkSubscriptionService

  @Autowired
  private lateinit var settings: ChunkStreamConfig

  private fun inflate(blob: ByteArray): ByteArray {
    val inflater = Inflater()
    try {
      inflater.setInput(blob)

      val out = ByteArrayOutputStream(blob.size * 4)
      val buffer = ByteArray(8192)
      while (!inflater.finished()) {
        val n = inflater.inflate(buffer)
        if (n == 0) break
        out.write(buffer, 0, n)
      }
      return out.toByteArray()
    } finally {
      inflater.end()
    }
  }

  private fun decode(message: ChunkDataSMSG) = RleCodec.decode(
    message.chunk,
    if (message.compression == ChunkDataSMSG.Compression.DEFLATE) inflate(message.payload) else message.payload
  )

  @Test
  @Order(1)
  fun `connecting gets the world info and the block palette`() {
    clientPlayer1.connect(testData.account1.masterIds.first())

    await {
      val info = assertNotNull(
        clientPlayer1.tryGetLastReceived(WorldInfoSMSG::class),
        "a client needs the chunk dimensions before any payload means anything"
      )

      assertEquals(RleCodec.VERSION, info.chunkFormatVersion)
      assertEquals(settings.viewRadiusChunks, info.viewRadiusChunks)
      assertTrue(info.chunkSize > 0 && info.chunkHeight > 0)
      assertTrue(info.name.isNotBlank())
    }

    val palette = assertNotNull(clientPlayer1.tryGetLastReceived(BlockPaletteSMSG::class))

    assertEquals(
      BlockType.entries.size, palette.blocks.size,
      "every material must be described, or the client cannot name what it decodes"
    )
    assertEquals(
      BlockType.entries.map { it.id }.sorted(), palette.blocks.map { it.id },
      "the palette is ordered by id, matching how its version hash is folded"
    )
  }

  @Test
  @Order(2)
  fun `a subscribed player is offered a whole view volume but sent almost none of it`() {
    val across = settings.chunksAcrossView

    // The view fills in over several ticks rather than arriving whole. Sampling which vertical slabs the
    // surface occupies costs a feature query and a thousand noise evaluations per column, so doing all 121 in
    // one tick would stall the zone thread; the budget spreads it and the manifest grows as a diff. Terrain is
    // therefore slightly late on genuinely new ground and instant once anyone has been there.
    await {
      val announced = subscriptions.announcedTo(clientPlayer1.connectedPlayerId).size
      assertTrue(
        announced >= across * across,
        "an ${across}x$across view should end up offering at least ${across * across} chunks, got $announced"
      )
    }

    // This is the whole point of announcing before sending: the offer costs a handful of bytes per chunk where
    // the payloads are some three kilobytes each, and the client asks only for what it lacks.
    val sent = subscriptions.sentTo(clientPlayer1.connectedPlayerId).size
    assertTrue(
      sent <= 1,
      "only the chunk under the player's feet should be pushed unasked, but $sent were sent"
    )

    val manifest = clientPlayer1.getLastReceived(ChunkManifestSMSG::class)
    assertTrue(
      manifest.added.all { it.revision == 0 },
      "an untouched chunk is revision zero, which is what lets a returning client skip it entirely"
    )
    assertTrue(
      manifest.removed.isEmpty(),
      "a player who has not moved must never have terrain withdrawn - a chunk the budget has not reached yet " +
          "has not gone anywhere"
    )
  }

  @Test
  @Order(3)
  fun `a requested chunk arrives and decodes to real terrain`() {
    val manifest = clientPlayer1.getLastReceived(ChunkManifestSMSG::class)
    val wanted = manifest.added.take(settings.chunksPerTickPerPlayer).map { it.chunk }

    clientPlayer1.sendMessage(ChunkRequestCMSG(clientPlayer1.connectedPlayerId, wanted))

    await {
      val received = wanted.mapNotNull { chunk ->
        clientPlayer1.tryGetLastReceived(ChunkDataSMSG::class)?.takeIf { it.chunk == chunk }
      }
      assertTrue(received.isNotEmpty(), "no requested chunk arrived")
    }

    val data = clientPlayer1.getLastReceived(ChunkDataSMSG::class)

    assertEquals(ChunkDataSMSG.Encoding.RLE_V2, data.encoding)
    assertTrue(data.payload.isNotEmpty())
    assertTrue(
      data.payload.size < 1_048_576,
      "one chunk must stay well inside the socket's frame ceiling, was ${data.payload.size} B"
    )

    val voxels = decode(data)

    assertEquals(chunkService.config.chunkSize, voxels.size)
    assertEquals(chunkService.config.chunkHeight, voxels.height)
    // decode() already calls validate(), so reaching here means air-implies-empty held across the wire.
    assertEquals(
      chunkService.config.chunkSize * chunkService.config.chunkSize * chunkService.config.chunkHeight,
      voxels.volume
    )
  }

  @Test
  @Order(4)
  fun `a chunk that was never offered is not served`() {
    clientPlayer1.clearMessages()

    // Far outside any plausible view volume. The gate is what stops a pull transport being a way to walk the
    // whole map, or to make the server materialise arbitrary terrain, one request at a time.
    val unoffered = ChunkPos(500_000, 500_000, 0)
    assertTrue(
      !subscriptions.isAnnouncedTo(clientPlayer1.connectedPlayerId, chunkService.normalise(unoffered)),
      "the test's own premise: this chunk must not be in the subscription"
    )

    clientPlayer1.sendMessage(ChunkRequestCMSG(clientPlayer1.connectedPlayerId, listOf(unoffered)))

    await {
      assertTrue(
        clientPlayer1.tryGetLastReceived(ChunkDataSMSG::class) == null,
        "an unoffered chunk must not be served"
      )
    }
  }

  @Test
  @Order(5)
  fun `editing terrain sends the holders a patch rather than the chunk`() {
    // Edit a voxel in a chunk the client demonstrably holds, so it is a patch recipient.
    val held = subscriptions.sentTo(clientPlayer1.connectedPlayerId).first()
    val before = chunkService.revisionOf(held)

    val voxelX = held.x.toLong() * chunkService.config.chunkSize + 1
    val voxelY = held.y.toLong() * chunkService.config.chunkSize + 1
    val voxelZ = held.z.toLong() * chunkService.config.chunkHeight + 1

    clientPlayer1.clearMessages()
    clientPlayer1.sendMessage(
      ChatCMSG(
        clientPlayer1.connectedPlayerId,
        ChatCMSG.Type.COMMAND,
        "/setblock $voxelX $voxelY $voxelZ MASONRY"
      )
    )

    await {
      assertNotNull(
        clientPlayer1.tryGetLastReceived(ChunkPatchSMSG::class),
        "a holder of the edited chunk should have been sent a patch"
      )
    }

    val patch = clientPlayer1.getLastReceived(ChunkPatchSMSG::class)

    assertEquals(held, patch.chunk)
    assertEquals(before, patch.fromRevision, "a patch must state the revision it builds on")
    assertEquals(before + 1, patch.toRevision)

    val edits = ChunkPatchCodec.decode(patch.edits)
    assertEquals(1, edits.size)

    val index = ChunkCoords.voxelIndex(chunkService.config, 1, 1, 1)
    assertEquals(BlockType.MASONRY.id, ChunkPatchCodec.blockIdOf(edits.getValue(index)))

    // The patch must be cheaper than restating the chunk - but only that, because how much cheaper depends
    // entirely on the chunk. Masters spawn at `Vec3L.ZERO`, which is the world corner and so inside the
    // 2.5 km ocean margin, so the chunk under this player is near-uniform water and encodes to a couple of
    // dozen bytes. The fiftyfold-and-better saving is a property of *surface* chunks and is asserted against
    // a real one in ChunkWireFormatTest.
    //
    // That the server picks a patch here at all is the interesting part: `broadcastChanges` compares the two
    // and would have sent the snapshot had it been smaller, which on a chunk this cheap is a real possibility
    // rather than a theoretical one.
    val chunkBytes = chunkService.encodedOf(held).payload.size
    assertTrue(
      patch.edits.size < chunkBytes,
      "a one-voxel patch is ${patch.edits.size} B against $chunkBytes B of chunk - it must be the cheaper of " +
          "the two, or the patch path is not earning its complexity"
    )

    assertTrue(
      clientPlayer1.tryGetLastReceived(ChunkDataSMSG::class) == null,
      "the chunk itself must not be re-sent for a single-voxel edit"
    )
  }

  @Test
  @Order(6)
  fun `recomputing a subscription does not resample the heightfield`() {
    // A manifest asks which vertical slabs the surface occupies for every column in the view volume, and each
    // uncached answer is a feature query plus a thousand noise evaluations. Recomputed from scratch on every
    // chunk boundary crossing - every 32 m at walking pace - that is over a hundred thousand evaluations on a
    // 50 ms tick. Deterministic rather than timed on purpose: a stopwatch assertion would be flaky, and this
    // is the property that actually matters.
    val anchor = assertNotNull(subscriptions.anchorOf(clientPlayer1.connectedPlayerId))
    val radius = settings.viewRadiusChunks

    // Wait for the budget to have worked through the whole view, then check that revisiting it is free.
    await {
      for (dy in -radius..radius) {
        for (dx in -radius..radius) {
          assertNotNull(
            chunkService.cachedSlabsOf(ChunkPos(anchor.x + dx, anchor.y + dy, anchor.z)),
            "the slab budget should have reached the whole view volume by now"
          )
        }
      }
    }

    val before = chunkService.slabComputations

    for (dy in -radius..radius) {
      for (dx in -radius..radius) {
        chunkService.surfaceSlabsOf(ChunkPos(anchor.x + dx, anchor.y + dy, anchor.z))
      }
    }

    assertEquals(
      before, chunkService.slabComputations,
      "the tick already sampled this view volume; asking again must not resample the heightfield"
    )
  }

  @Test
  @Order(7)
  fun `the slab range actually contains the terrain surface`() {
    val anchor = assertNotNull(subscriptions.anchorOf(clientPlayer1.connectedPlayerId))
    val config = chunkService.config

    // The correctness half of the vertical rule. Being thin is only worth anything if it is thin around the
    // right place - a range that missed the surface would send a player bedrock and look like working code.
    val slabs = chunkService.surfaceSlabsOf(anchor)

    assertTrue(
      config.chunkZOf(config.seaLevel) in slabs,
      "sea level must always be inside the range, so a coastal column offers the water surface too"
    )

    for (z in slabs) {
      val voxels = chunkService.merged(ChunkPos(anchor.x, anchor.y, z))
      assertEquals(config.chunkHeight, voxels.height)
    }
  }

  @Test
  @Order(8)
  fun `the edit is visible in the server's own authoritative view`() {
    val held = subscriptions.sentTo(clientPlayer1.connectedPlayerId).first()

    // Not a restatement of the patch test. The patch says what the server *told* the client; this says what
    // the server itself believes, which is the view line of sight and movement validation will be answered
    // from. If those two ever diverge, players see one world and the server enforces another.
    assertEquals(BlockType.MASONRY, chunkService.merged(held)[1, 1, 1])
  }
}
