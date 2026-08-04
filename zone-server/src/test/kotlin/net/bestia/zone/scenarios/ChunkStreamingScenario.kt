package net.bestia.zone.scenarios

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.ChunkDelta
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.worldgen.voxel.ChunkEngine
import net.bestia.worldgen.voxel.RleCodec
import net.bestia.zone.chat.ChatCMSG
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

  /**
   * The chunk and voxel the carve test aimed at, so the authoritative-view test can check the same spot.
   *
   * Carried between tests because these are ordered and cumulative by design, and because where the brush
   * landed is discovered at run time - see [carvable].
   */
  private lateinit var carvedChunk: ChunkPos
  private var carvedIndex = -1

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
  fun `connecting gets the world info`() {
    clientPlayer1.connect(testData.account1.masterIds.first())

    await {
      val info = assertNotNull(
        clientPlayer1.tryGetLastReceived(WorldInfoSMSG::class),
        "a client needs the chunk dimensions before any payload means anything"
      )

      // One number, not the server's three-part version vector. The client holds a static palette and a
      // decoder; either they match what is being sent or it must be updated, and it has no use for knowing
      // which half disagreed.
      assertEquals(ChunkEngine.VERSION, info.chunkEngineVersion)
      assertEquals(settings.viewRadiusChunks, info.viewRadiusChunks)
      assertTrue(info.chunkSize > 0 && info.chunkHeight > 0)
      assertTrue(info.name.isNotBlank())
    }
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

    val barrier = subscriptions.announcedTo(clientPlayer1.connectedPlayerId).first()

    clientPlayer1.sendMessage(ChunkRequestCMSG(clientPlayer1.connectedPlayerId, listOf(unoffered)))
    // A second, legitimately-offered request queued right behind it. Requests are served in arrival order, so
    // once this one's answer shows up, the unoffered chunk's fate has already been decided.
    //
    // This can't be proven by retrying a single "nothing arrived" assertion with `await`/`untilAsserted`:
    // untilAsserted stops on the first pass, and a ChunkDataSMSG that is genuinely unrelated (queued earlier in
    // the scenario, arriving late) can turn up mid-retry and fail every attempt until the timeout even though
    // the gate held throughout. Settling on the barrier response first, then checking once, sidesteps that.
    clientPlayer1.sendMessage(ChunkRequestCMSG(clientPlayer1.connectedPlayerId, listOf(barrier)))

    await {
      assertTrue(
        clientPlayer1.receivedAny(ChunkDataSMSG::class) { it.chunk == barrier },
        "barrier chunk should have arrived by now"
      )
    }

    assertTrue(
      !clientPlayer1.receivedAny(ChunkDataSMSG::class) { it.chunk == chunkService.normalise(unoffered) },
      "an unoffered chunk must not be served"
    )
  }

  /**
   * A voxel with material in it, far enough from every face that a minimum-radius brush stays in one chunk.
   *
   * Searched rather than hard-coded, because where the material is depends entirely on where the player spawned.
   * Masters spawn at `Vec3L.ZERO`, the world corner, which is inside the 2.5 km ocean margin - so the chunk
   * under this player is near-uniform water and a fixed coordinate would be as likely to name open air as rock.
   * Carving air is correctly a no-op, so that would produce no patch and a test that fails for the wrong reason.
   */
  private fun carvable(chunk: ChunkPos): Triple<Int, Int, Int>? {
    val config = chunkService.config
    val voxels = chunkService.merged(chunk)
    val margin = CarveBrush.MIN_RADIUS.toInt() + 1

    for (localZ in margin until config.chunkHeight - margin) {
      for (localY in margin until config.chunkSize - margin) {
        for (localX in margin until config.chunkSize - margin) {
          if (voxels.occupancyAt(localX, localY, localZ) > 0) return Triple(localX, localY, localZ)
        }
      }
    }

    return null
  }

  @Test
  @Order(5)
  fun `carving terrain sends the holders a patch rather than the chunk`() {
    // Carve inside a chunk the client demonstrably holds, so it is a patch recipient.
    val candidates = subscriptions.sentTo(clientPlayer1.connectedPlayerId)
    val target = candidates.firstNotNullOfOrNull { chunk -> carvable(chunk)?.let { chunk to it } }

    assertNotNull(target, "none of the ${candidates.size} held chunks had material to carve")

    val (held, local) = target
    val (localX, localY, localZ) = local
    val before = chunkService.revisionOf(held)

    carvedChunk = held
    carvedIndex = ChunkCoords.voxelIndex(chunkService.config, localX, localY, localZ)

    val voxelX = held.x.toLong() * chunkService.config.chunkSize + localX
    val voxelY = held.y.toLong() * chunkService.config.chunkSize + localY
    val voxelZ = held.z.toLong() * chunkService.config.chunkHeight + localZ

    clientPlayer1.clearMessages()
    clientPlayer1.sendMessage(
      ChatCMSG(
        clientPlayer1.connectedPlayerId,
        ChatCMSG.Type.COMMAND,
        "/carve $voxelX $voxelY $voxelZ ${CarveBrush.MIN_RADIUS}"
      )
    )

    await {
      assertNotNull(
        clientPlayer1.tryGetLastReceived(ChunkPatchSMSG::class),
        "a holder of the carved chunk should have been sent a patch"
      )
    }

    val patch = clientPlayer1.getLastReceived(ChunkPatchSMSG::class)

    assertEquals(held, patch.chunk)
    assertEquals(before, patch.fromRevision, "a patch must state the revision it builds on")
    assertEquals(before + 1, patch.toRevision)

    val removals = ChunkPatchCodec.decode(patch.removals)
    assertEquals(patch.removalCount, removals.size, "the carried count must match what decodes")

    // A brush is a sphere, so one swing is many voxels - and the voxel it was aimed at is necessarily one of
    // them, because the centre of a sphere is inside it.
    assertTrue(removals.size > 1, "a minimum-radius brush took only ${removals.size} voxels")

    assertTrue(
      removals.any { ChunkDelta.indexOf(it) == carvedIndex },
      "the voxel the brush was centred on is not in the patch"
    )

    // The patch must be cheaper than restating the chunk - but only that, because how much cheaper depends
    // entirely on the chunk. Near-uniform ocean encodes to a couple of dozen bytes, so the fiftyfold-and-better
    // saving is a property of *surface* chunks and is asserted against a real one in ChunkWireFormatTest.
    //
    // That the server picks a patch here at all is the interesting part: `broadcastChanges` compares the two
    // and would have sent the snapshot had it been smaller, which on a chunk this cheap is a real possibility
    // rather than a theoretical one.
    val chunkBytes = chunkService.encodedOf(held).payload.size
    assertTrue(
      patch.removals.size < chunkBytes,
      "a ${removals.size}-voxel patch is ${patch.removals.size} B against $chunkBytes B of chunk - it must be " +
          "the cheaper of the two, or the patch path is not earning its complexity"
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
  fun `the slabs actually contain the terrain surface`() {
    val anchor = assertNotNull(subscriptions.anchorOf(clientPlayer1.connectedPlayerId))
    val config = chunkService.config

    // The correctness half of the vertical rule. Being thin is only worth anything if it is thin around the
    // right place - a set that missed the surface would send a player bedrock and look like working code.
    val slabs = chunkService.surfaceSlabsOf(anchor)

    assertTrue(
      config.chunkZOf(config.seaLevel) in slabs,
      "sea level must always be offered, so a coastal column offers the water surface too"
    )

    for (z in slabs) {
      val voxels = chunkService.merged(ChunkPos(anchor.x, anchor.y, z))
      assertEquals(config.chunkHeight, voxels.height)
    }
  }

  @Test
  @Order(8)
  fun `the view volume is bounded vertically however deep the water is`() {
    val anchor = chunkService.normalise(assertNotNull(subscriptions.anchorOf(clientPlayer1.connectedPlayerId)))
    val vertical = settings.viewRadiusChunksVertical
    val announced = subscriptions.announcedTo(clientPlayer1.connectedPlayerId)

    // The 726-chunk regression, stated as the invariant that would have caught it rather than as a claim
    // about one column - the seed is drawn at random on each boot, so how deep any particular piece of
    // seabed is varies between runs and is not a thing to assert on.
    //
    // What used to happen: `computeSurfaceSlabs` returned the *span* from a column's seabed up to the
    // waterline, and `desiredChunks` unioned the player's own slab ±1 on top. In the 2.5 km ocean margin
    // that is six slabs per column, five of them solid water the client meshes to nothing - and it grew by
    // one more for every 256 m of depth. 726 chunks offered for a view that draws 121.
    val outside = announced.filter { it.z < anchor.z - vertical || it.z > anchor.z + vertical }
    assertTrue(
      outside.isEmpty(),
      "nothing outside ${anchor.z} ± $vertical may be offered, however far the surface rule reaches; " +
          "got ${outside.take(5)}"
    )

    val perColumn = announced.groupingBy { it.x to it.y }.eachCount()
    assertTrue(
      perColumn.values.all { it <= 2 * vertical + 1 },
      "a column may cost at most ${2 * vertical + 1} slabs, but one cost ${perColumn.values.max()}"
    )

    // The other half of the rule, and the reason the clip is safe: the player's own slab survives it
    // unconditionally, so there is ground underfoot even where the heightfield describes something else.
    assertTrue(
      anchor in announced,
      "the chunk the player is standing in must always be offered"
    )
  }

  @Test
  @Order(9)
  fun `the carve is visible in the server's own authoritative view`() {
    // Not a restatement of the patch test. The patch says what the server *told* the client; this says what
    // the server itself believes, which is the view line of sight and movement validation will be answered
    // from. If those two ever diverge, players see one world and the server enforces another.
    //
    // Against the voxel the brush was centred on, remembered from the carve test, because which chunk it landed
    // in was discovered at run time from where the material happened to be - see `carvable`.
    assertTrue(carvedIndex >= 0, "the carve test must run first; these are ordered and cumulative")

    val merged = chunkService.merged(carvedChunk)

    // The centre of a sphere is inside it, so this voxel was taken whole.
    assertEquals(
      0,
      merged.occupancy[carvedIndex].toInt() and 0xFF,
      "the voxel at the centre of the brush should have been removed entirely"
    )
  }
}
