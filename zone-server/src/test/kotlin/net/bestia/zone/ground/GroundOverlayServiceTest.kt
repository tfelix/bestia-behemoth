package net.bestia.zone.ground

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.ground.fire.GroundFireService
import net.bestia.zone.message.ChunkFanOut
import net.bestia.zone.message.SMSG
import net.bestia.zone.world.residency.ChunkResidency
import net.bestia.zone.world.stream.ChunkSubscriptionService
import net.bestia.zone.world.time.BestiaClock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * When a client is told about the ground: at once for what it cannot work out, on entry for what it can, and
 * a rare resync for the drift in between.
 *
 * Time is a variable the tests move, because the throttle is the whole property - a service that sent every
 * footfall would be correct on the wire and still be the fifty messages a second this exists to stop.
 */
class GroundOverlayServiceTest {

  private val resyncSeconds = 180L

  private val column = ColumnKey.of(0, 0)
  private val chunk = ChunkPos(0, 0, 0)

  private val alice = 1L
  private val bob = 2L

  private var clockSecond = 1_000L

  private val subscriptions = ChunkSubscriptionService(ChunkResidency())
  private val fanOut = RecordingFanOut()

  private val fire = mockk<GroundFireService> {
    every { burningIn(any(), any()) } returns null
  }

  private val scorch = FakeLayer(GroundLayer.SCORCHED, GroundLayerEncoding.NIBBLE_V1)
  private val wear = FakeLayer(GroundLayer.WORN, GroundLayerEncoding.LEVELS_SPARSE_V1)
  private val blood = FakeLayer(GroundLayer.BLOODIED, GroundLayerEncoding.LEVELS_SPARSE_V1)
  private val prints = FakeStamps()

  private val clock = mockk<BestiaClock> {
    every { now() } answers { mockk { every { absoluteSecond } returns clockSecond } }
  }

  private fun service(resyncIntervalSeconds: Long = resyncSeconds): GroundOverlayService {
    return GroundOverlayService(
      fanOut,
      subscriptions,
      fire,
      listOf(scorch, wear, blood),
      listOf(prints),
      GroundSyncConfig(resyncIntervalSeconds),
      clock
    )
  }

  private fun hold(account: Long) {
    subscriptions.markSent(account, chunk)
  }

  private fun flushAt(sut: GroundOverlayService, atSecond: Long): Int {
    clockSecond = atSecond
    fanOut.sent.clear()
    return sut.flush()
  }

  @Test
  fun `a newcomer is sent every mark the column has, in one layers message`() {
    scorch.fill(column)
    wear.fill(column)
    prints.fill(column)
    val sut = service()

    hold(alice)

    assertEquals(2, flushAt(sut, 1_000))

    val (recipients, layers) = fanOut.layerMessages().single()
    assertEquals(setOf(alice), recipients)
    assertEquals(listOf(GroundLayer.SCORCHED, GroundLayer.WORN), layers.layers.map { it.layer })
    assertEquals(setOf(alice), fanOut.stampMessages().single().first)
    assertEquals(0, sut.pending)
  }

  /** Silence means clean: a message per chunk per login to say "nothing happened here" is what this avoids. */
  @Test
  fun `a newcomer to untouched ground is sent nothing at all`() {
    val sut = service()

    hold(alice)

    assertEquals(0, sut.pending)
    assertEquals(0, flushAt(sut, 1_000))
  }

  @Test
  fun `a newcomer is told about a fire that is alight`() {
    every { fire.burningIn(0, 0) } returns ColumnMask(32).also { it.set(1, 1) }
    val sut = service()

    hold(alice)

    assertEquals(1, flushAt(sut, 1_000))
    assertIs<ChunkGroundOverlaySMSG>(fanOut.sent.single().second)
  }

  /** The snapshot has to arrive on the tick it is owed, however much resync is waiting in the same column. */
  @Test
  fun `a newcomer's snapshot is never delayed by a resync already waiting`() {
    val sut = service()
    hold(alice)
    sut.markLevelsDirty(column, GroundLayer.WORN, nowSecond = 1_000)

    wear.fill(column)
    hold(bob)

    assertEquals(1, flushAt(sut, 1_001), "the resync was not due until ${1_000 + resyncSeconds}")
    assertEquals(setOf(alice, bob), fanOut.layerMessages().single().first)
  }

  @Test
  fun `a resync that rode out with a snapshot is not sent again when it was due`() {
    val sut = service()
    hold(alice)
    sut.markLevelsDirty(column, GroundLayer.WORN, nowSecond = 1_000)
    wear.fill(column)
    hold(bob)
    flushAt(sut, 1_001)

    assertEquals(0, flushAt(sut, 1_000 + resyncSeconds))
    assertEquals(0, sut.pending)
  }

  @Test
  fun `footprints are resynced one interval after the first change, not on it`() {
    val sut = service()
    hold(alice)
    prints.fill(column)

    sut.markStampsDirty(column, nowSecond = 1_000)

    assertEquals(1, sut.pending)
    assertEquals(0, flushAt(sut, 1_000), "the first footfall was sent at once")
    assertEquals(0, flushAt(sut, 1_000 + resyncSeconds - 1))
    assertEquals(1, sut.pending, "waiting must not lose the column")

    assertEquals(1, flushAt(sut, 1_000 + resyncSeconds))
    assertEquals(setOf(alice), fanOut.stampMessages().single().first)
    assertEquals(0, sut.pending)
  }

  @Test
  fun `a run of footfalls is one resync, due from the first`() {
    val sut = service()
    hold(alice)
    prints.fill(column)

    sut.markStampsDirty(column, nowSecond = 1_000)
    sut.markStampsDirty(column, nowSecond = 1_050)
    sut.markStampsDirty(column, nowSecond = 1_170)

    assertEquals(1, sut.pending)
    assertEquals(1, flushAt(sut, 1_000 + resyncSeconds), "later footfalls pushed the first one's resync back")
    assertEquals(0, flushAt(sut, 1_170 + resyncSeconds), "the later footfalls were sent a second time")
  }

  @Test
  fun `a footfall after a resync starts the next interval`() {
    val sut = service()
    hold(alice)
    prints.fill(column)

    sut.markStampsDirty(column, nowSecond = 1_000)
    flushAt(sut, 1_000 + resyncSeconds)

    sut.markStampsDirty(column, nowSecond = 1_200)

    assertEquals(0, flushAt(sut, 1_200 + resyncSeconds - 1))
    assertEquals(1, flushAt(sut, 1_200 + resyncSeconds))
  }

  @Test
  fun `wear is resynced the same way`() {
    val sut = service()
    hold(alice)
    wear.fill(column)

    sut.markLevelsDirty(column, GroundLayer.WORN, nowSecond = 1_000)
    sut.markLevelsDirty(column, GroundLayer.WORN, nowSecond = 1_100)

    assertEquals(0, flushAt(sut, 1_000 + resyncSeconds - 1))
    assertEquals(1, flushAt(sut, 1_000 + resyncSeconds))

    val layers = fanOut.layerMessages().single().second.layers
    assertEquals(listOf(GroundLayer.WORN), layers.map { it.layer })
  }

  @Test
  fun `blood is resynced the same way`() {
    val sut = service()
    hold(alice)
    blood.fill(column)

    sut.markLevelsDirty(column, GroundLayer.BLOODIED, nowSecond = 1_000)

    assertEquals(0, flushAt(sut, 1_000 + resyncSeconds - 1))
    assertEquals(1, flushAt(sut, 1_000 + resyncSeconds))
    assertEquals(listOf(GroundLayer.BLOODIED), fanOut.layerMessages().single().second.layers.map { it.layer })
  }

  /** Wear and blood each wait for their own resync, so one going out does not carry the other's bytes along. */
  @Test
  fun `wear and blood fall due on their own clocks`() {
    val sut = service()
    hold(alice)
    wear.fill(column)
    blood.fill(column)

    sut.markLevelsDirty(column, GroundLayer.WORN, nowSecond = 1_000)
    sut.markLevelsDirty(column, GroundLayer.BLOODIED, nowSecond = 1_100)

    flushAt(sut, 1_000 + resyncSeconds)
    assertEquals(listOf(GroundLayer.WORN), fanOut.layerMessages().single().second.layers.map { it.layer })

    flushAt(sut, 1_100 + resyncSeconds)
    assertEquals(listOf(GroundLayer.BLOODIED), fanOut.layerMessages().single().second.layers.map { it.layer })
  }

  @Test
  fun `layers that fall due together for the same people share a message`() {
    val sut = service()
    hold(alice)
    wear.fill(column)
    blood.fill(column)

    sut.markLevelsDirty(column, GroundLayer.WORN, nowSecond = 1_000)
    sut.markLevelsDirty(column, GroundLayer.BLOODIED, nowSecond = 1_000)

    assertEquals(1, flushAt(sut, 1_000 + resyncSeconds))
    assertEquals(
      listOf(GroundLayer.WORN, GroundLayer.BLOODIED),
      fanOut.layerMessages().single().second.layers.map { it.layer }
    )
  }

  @Test
  fun `layers owed to different people are not mixed`() {
    val sut = service()
    hold(alice)
    wear.fill(column)
    sut.markLevelsDirty(column, GroundLayer.WORN, nowSecond = 1_000)

    // Bob arrives onto a scar, and wear that alice is still waiting on.
    scorch.fill(column)
    hold(bob)

    assertEquals(2, flushAt(sut, 1_001))

    val byRecipients = fanOut.layerMessages().associate { it.first to it.second.layers.map { entry -> entry.layer } }
    assertEquals(listOf(GroundLayer.SCORCHED), byRecipients[setOf(bob)])
    assertEquals(listOf(GroundLayer.WORN), byRecipients[setOf(alice, bob)])
  }

  @Test
  fun `everyone holding the column gets one encode between them`() {
    val sut = service()
    hold(alice)
    hold(bob)
    prints.fill(column)

    sut.markStampsDirty(column, nowSecond = 1_000)

    assertEquals(1, flushAt(sut, 1_000 + resyncSeconds))
    assertEquals(setOf(alice, bob), fanOut.stampMessages().single().first)
  }

  /** A scar follows a fire a client cannot simulate, so it is not paced by the resync. */
  @Test
  fun `a scar goes out on the next flush and alone`() {
    val sut = service()
    hold(alice)
    wear.fill(column)
    scorch.fill(column)

    sut.markLayerDirty(column, GroundLayer.SCORCHED)

    assertEquals(1, flushAt(sut, 1_000))
    val layers = fanOut.layerMessages().single().second.layers
    assertEquals(listOf(GroundLayer.SCORCHED), layers.map { it.layer }, "the wear beside it was dragged along")
  }

  @Test
  fun `a scar going out leaves the wear resync waiting`() {
    val sut = service()
    hold(alice)
    wear.fill(column)
    scorch.fill(column)
    sut.markLevelsDirty(column, GroundLayer.WORN, nowSecond = 1_000)
    sut.markLayerDirty(column, GroundLayer.SCORCHED)

    flushAt(sut, 1_001)

    assertEquals(1, sut.pending, "the wear resync was sent early or lost")
    assertEquals(1, flushAt(sut, 1_000 + resyncSeconds))
    assertEquals(listOf(GroundLayer.WORN), fanOut.layerMessages().single().second.layers.map { it.layer })
  }

  /** The only way a healed scar retires on a client. */
  @Test
  fun `a layer that has emptied is sent as an empty entry`() {
    val sut = service()
    hold(alice)

    sut.markLayerDirty(column, GroundLayer.SCORCHED)
    flushAt(sut, 1_000)

    val entry = fanOut.layerMessages().single().second.layers.single()
    assertEquals(GroundLayer.SCORCHED, entry.layer)
    assertTrue(entry.isEmpty)
    assertEquals(GroundLayerEncoding.NIBBLE_V1, entry.encoding)
  }

  @Test
  fun `wear that has faded away since it was marked is resynced as an empty entry`() {
    val sut = service()
    hold(alice)
    wear.fill(column)
    sut.markLevelsDirty(column, GroundLayer.WORN, nowSecond = 1_000)
    wear.clear(column)

    flushAt(sut, 1_000 + resyncSeconds)

    assertTrue(fanOut.layerMessages().single().second.layers.single().isEmpty)
  }

  @Test
  fun `what is alight goes out on the next flush`() {
    every { fire.burningIn(0, 0) } returns ColumnMask(32).also { it.set(2, 2) }
    val sut = service()
    hold(alice)

    sut.markDirty(column)

    assertEquals(1, flushAt(sut, 1_000))
    val (recipients, message) = fanOut.sent.single()
    assertEquals(setOf(alice), recipients)
    assertNotNull(assertIs<ChunkGroundOverlaySMSG>(message).burning)
  }

  @Test
  fun `a fire that has gone out is sent as no mask at all`() {
    val sut = service()
    hold(alice)

    sut.markDirty(column)
    flushAt(sut, 1_000)

    assertNull(assertIs<ChunkGroundOverlaySMSG>(fanOut.sent.single().second).burning)
  }

  /** `0` is "never resync": a client still gets what a column holds on entry, and a scar the moment it forms. */
  @Test
  fun `with the resync off the throttled changes are never sent`() {
    val sut = service(resyncIntervalSeconds = 0)
    hold(alice)
    wear.fill(column)
    blood.fill(column)
    prints.fill(column)

    sut.markStampsDirty(column, nowSecond = 1_000)
    sut.markLevelsDirty(column, GroundLayer.WORN, nowSecond = 1_000)
    sut.markLevelsDirty(column, GroundLayer.BLOODIED, nowSecond = 1_000)

    assertEquals(0, sut.pending)
    assertEquals(0, flushAt(sut, 1_000 + 100 * resyncSeconds))
  }

  @Test
  fun `with the resync off a newcomer still gets the snapshot`() {
    wear.fill(column)
    prints.fill(column)
    val sut = service(resyncIntervalSeconds = 0)

    hold(alice)

    assertEquals(2, flushAt(sut, 1_000))
    assertEquals(setOf(alice), fanOut.layerMessages().single().first)
    assertEquals(setOf(alice), fanOut.stampMessages().single().first)
  }

  @Test
  fun `with the resync off a scar still goes out at once`() {
    val sut = service(resyncIntervalSeconds = 0)
    hold(alice)
    scorch.fill(column)

    sut.markLayerDirty(column, GroundLayer.SCORCHED)

    assertEquals(1, flushAt(sut, 1_000))
  }

  /** What `GroundLevelDecaySystem.drainLoaded` relies on: a late snapshot is due now, resync or no resync. */
  @Test
  fun `a layer marked dirty directly is sent at once even with the resync off`() {
    val sut = service(resyncIntervalSeconds = 0)
    hold(alice)
    wear.fill(column)

    sut.markLayerDirty(column, GroundLayer.WORN)

    assertEquals(1, flushAt(sut, 1_000))
    assertEquals(listOf(GroundLayer.WORN), fanOut.layerMessages().single().second.layers.map { it.layer })
  }

  @Test
  fun `nobody holding a column means nothing is owed for it`() {
    val sut = service()

    sut.markStampsDirty(column, nowSecond = 1_000)
    sut.markLevelsDirty(column, GroundLayer.WORN, nowSecond = 1_000)
    sut.markLayerDirty(column, GroundLayer.SCORCHED)
    sut.markDirty(column)

    assertEquals(0, sut.pending)
  }

  @Test
  fun `a column everyone has left is forgotten rather than kept for ever`() {
    val sut = service()
    hold(alice)
    sut.markStampsDirty(column, nowSecond = 1_000)
    sut.markLevelsDirty(column, GroundLayer.WORN, nowSecond = 1_000)
    assertEquals(2, sut.pending)

    subscriptions.unsend(alice, chunk)

    assertEquals(0, sut.pending)
  }

  /** Bob holds another slab of the same column, so the column is not abandoned when alice lets go of hers. */
  @Test
  fun `a client that left while others stay is dropped from what is owed`() {
    val sut = service()
    hold(alice)
    subscriptions.markSent(bob, ChunkPos(0, 0, 1))
    prints.fill(column)
    sut.markStampsDirty(column, nowSecond = 1_000)

    subscriptions.unsend(alice, chunk)

    assertEquals(1, flushAt(sut, 1_000 + resyncSeconds))
    assertEquals(setOf(bob), fanOut.stampMessages().single().first)
  }

  @Test
  fun `a flush with nothing due sends nothing and keeps what is waiting`() {
    val sut = service()
    hold(alice)
    sut.markStampsDirty(column, nowSecond = 1_000)

    repeat(5) { assertEquals(0, flushAt(sut, 1_001L + it)) }

    assertEquals(1, sut.pending)
  }

  @Test
  fun `a stamp resync of a column whose prints have all expired is an empty message`() {
    val sut = service()
    hold(alice)
    sut.markStampsDirty(column, nowSecond = 1_000)

    flushAt(sut, 1_000 + resyncSeconds)

    assertNull(fanOut.stampMessages().single().second.stamps)
  }

  private class RecordingFanOut : ChunkFanOut {

    val sent = mutableListOf<Pair<Set<Long>, SMSG>>()

    override fun fanOut(accountIds: Collection<Long>, message: SMSG): Int {
      sent += accountIds.toSet() to message
      return accountIds.size
    }

    fun layerMessages(): List<Pair<Set<Long>, ChunkGroundLayersSMSG>> {
      return sent.filter { it.second is ChunkGroundLayersSMSG }.map { it.first to it.second as ChunkGroundLayersSMSG }
    }

    fun stampMessages(): List<Pair<Set<Long>, ChunkGroundStampsSMSG>> {
      return sent.filter { it.second is ChunkGroundStampsSMSG }.map { it.first to it.second as ChunkGroundStampsSMSG }
    }
  }

  private class FakeLayer(override val layer: GroundLayer, private val encoding: GroundLayerEncoding) : GroundLayerSource {

    private val entries = HashMap<Long, GroundLayerEntry>()

    fun fill(columnKey: Long) {
      entries[columnKey] = GroundLayerEntry(layer, encoding, byteArrayOf(1, 2, 3))
    }

    fun clear(columnKey: Long) {
      entries.remove(columnKey)
    }

    override fun entryAt(columnKey: Long): GroundLayerEntry {
      return entries[columnKey] ?: GroundLayerEntry.retired(layer, encoding)
    }
  }

  private class FakeStamps : GroundStampSource {

    private val columns = HashSet<Long>()

    fun fill(columnKey: Long) {
      columns += columnKey
    }

    override fun stampsAt(columnKey: Long): ByteArray? {
      return if (columnKey in columns) byteArrayOf(9, 9, 9, 9, 9) else null
    }
  }
}
