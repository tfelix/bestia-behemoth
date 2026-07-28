package net.bestia.zone.world.stream

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.socket.ChunkFanOut
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Drives chunk streaming: one pass per tick, on the thread that owns the chunk data.
 *
 * Ordered after `MoveSystem` so a player's subscription is computed from where they ended the tick rather
 * than where they started it.
 *
 * ### The order of the five steps is load bearing
 *
 * 1. **Apply queued edits.** They came off the network on another thread; this is the first point at which
 *    it is safe to touch the store.
 * 2. **Update subscriptions** and send manifests, so a client learns what it may ask for.
 * 3. **Broadcast changes** from step 1 to the clients that already hold the affected chunks.
 * 4. **Serve requests** that arrived on earlier ticks.
 * 5. **Spend the derived-rebuild budget.**
 *
 * Three and four are in that order on purpose. A client served in step four is marked as holding the chunk
 * at its *current* revision, which already includes this tick's edits - so it must not also receive a patch
 * describing them. Serving first and broadcasting second would send it a patch whose `from_revision` is one
 * behind what it holds, which it would correctly reject and then re-request: correct, but a wasted round
 * trip on every edit that races a request.
 */
@SpringComponent
@Order(45)
class ChunkStreamSystem(
  private val chunkService: ChunkService,
  private val subscriptions: ChunkSubscriptionService,
  private val inbox: ChunkStreamInbox,
  private val fanOut: ChunkFanOut,
  private val settings: ChunkStreamConfig
) : System {

  override val reads: ComponentClassSet = setOf(Position::class, Account::class, ActivePlayer::class)

  /** Chunks a client has asked for and not yet been sent, nearest first. */
  private val queued = HashMap<Long, LinkedHashSet<ChunkPos>>()

  /** Token buckets, so a client cannot ask faster than it is served. */
  private val tokens = HashMap<Long, Int>()

  override fun update(world: World, deltaTime: Float) {
    if (!chunkService.isReady) return

    applyEdits()
    updateSubscriptions(world)
    broadcastChanges()
    serveRequests()
    chunkService.rebuildDerived()
  }

  // ---------------------------------------------------------------- step 1

  private fun applyEdits() {
    val edits = inbox.drainEdits()
    if (edits.isEmpty()) return

    val config = chunkService.config

    for (edit in edits) {
      val block = BlockType.ofOrNull(edit.blockId)
      if (block == null) {
        LOG.warn { "Account ${edit.accountId} asked for unknown block id ${edit.blockId}" }
        continue
      }

      val localised = ChunkCoords.localise(config, edit.x, edit.y, edit.z)
      if (localised == null) {
        LOG.warn { "Account ${edit.accountId} asked to edit (${edit.x},${edit.y},${edit.z}), off the grid" }
        continue
      }

      // Normalised, because a coordinate east of the eastern edge is a real place - the same place as one
      // in the west - and the generator must be asked about it under the name it knows.
      chunkService.setBlock(
        chunkService.normalise(localised.chunk),
        localised.localX,
        localised.localY,
        localised.localZ,
        block
      )
    }
  }

  // ---------------------------------------------------------------- step 2

  private fun updateSubscriptions(world: World) {
    val anchors = HashMap<Long, ChunkPos>()

    // Queried directly rather than through `WorldView.read`: a system already runs on the tick thread with
    // the full World, and taking the lock a second time from inside it is what that narrow view exists to
    // stop *other* threads doing, not this one.
    world.query(Position::class, Account::class, ActivePlayer::class).each {
      val accountId = get<Account>().accountId
      anchors[accountId] = ChunkCoords.chunkOf(chunkService.config, get<Position>().toVec3L())
    }

    // A connection with no anchor this tick has no active entity - it has not picked a master, or it just
    // went away. Drop its state rather than leave it subscribed to terrain nobody is standing in.
    subscriptions.trackedAccountIds().filterNot { it in anchors }.forEach { forget(it) }

    // Shared across every player this tick, because the cost being budgeted is the tick's, not any one
    // player's - and two players on the same fresh ground would otherwise pay for it twice.
    val budget = Budget(settings.slabComputationsPerTick)

    for ((accountId, anchor) in anchors) {
      val previous = subscriptions.anchorOf(accountId)
      subscriptions.setAnchor(accountId, anchor)

      // Run every tick, not only when the anchor moves. The desired set can grow while a player stands still,
      // because the slab budget may not have been able to afford the whole view volume yet.
      sendManifest(accountId, anchor, reset = previous == null, budget = budget)
    }
  }

  /** A tick's allowance of expensive slab computations. Mutable on purpose; one instance, shared. */
  private class Budget(var remaining: Int) {
    fun trySpend(): Boolean {
      if (remaining <= 0) return false
      remaining--
      return true
    }
  }

  /**
   * Recomputes the desired set and tells the client the difference.
   *
   * @param reset the client's whole set is being replaced, which is the case on the first manifest of a
   *   session and would also be the case after a teleport
   */
  private fun sendManifest(accountId: Long, anchor: ChunkPos, reset: Boolean, budget: Budget) {
    val desired = desiredChunks(anchor, budget)
    val held = subscriptions.announcedTo(accountId)

    val added = if (reset) desired.toList() else desired.filterNot { it in held }

    // Only withdraw what the view has genuinely left behind. A chunk absent from `desired` because the slab
    // budget ran out this tick has not gone anywhere, and withdrawing it would make the client discard terrain
    // it is standing next to only to be offered it again moments later. `reset` says everything, so it names
    // nothing removed.
    val viewColumns = viewColumnsAround(anchor)
    val removed = if (reset) {
      emptyList()
    } else {
      held.filterNot { it in desired || (it.x to it.y) in viewColumns }
    }

    if (added.isEmpty() && removed.isEmpty() && !reset) return

    val refs = added.map { ChunkManifestSMSG.Ref(it, chunkService.revisionOf(it)) }

    subscriptions.applyManifest(accountId, added, removed, reset)

    // Withdrawn chunks must also leave the send queue, or a client would be handed terrain it has just been
    // told to forget.
    queued[accountId]?.removeAll(removed.toSet())

    fanOut.sendTo(accountId, ChunkManifestSMSG(reset = reset, added = refs, removed = removed))

    // The chunk under the player's feet goes out unasked, at the head of the queue. Everything else waits to
    // be requested, but making the player wait a round trip for the ground they are standing on is the one
    // case where the saving is not worth it.
    //
    // Queued rather than written here, and it still leaves in this same tick - step four drains the queue.
    // Writing it here instead would mark the client as holding the chunk *before* step three broadcasts this
    // tick's edits, so an edit to this very chunk in step one would then be described by a patch the client
    // must reject: the exact race the three-before-four ordering exists to avoid. Going through the queue
    // costs nothing and keeps that argument free of special cases.
    if (reset) queueFirst(accountId, chunkService.normalise(anchor))

    LOG.debug {
      "Manifest for $accountId at $anchor: ${added.size} added, ${removed.size} removed, reset=$reset"
    }
  }

  /**
   * The chunks a player at [anchor] should hold.
   *
   * Horizontally a square of `viewRadiusChunks` around them. Vertically, the chunks the *terrain surface*
   * occupies in each column, plus the player's own slab and its neighbours.
   *
   * The vertical rule is not the obvious `z ± 1` box, which would triple the count for nothing: a chunk is
   * 256 m tall, so one slab almost always contains a whole column's surface. Asking the height sampler which
   * slab that is costs a raster lookup and means the player gets ground rather than bedrock even when their
   * own `z` is wrong - which it currently always is, because every master spawns at `Vec3L.ZERO`, at sea
   * level, whatever the terrain under them is doing.
   */
  private fun desiredChunks(anchor: ChunkPos, budget: Budget): Set<ChunkPos> {
    val radius = settings.viewRadiusChunks
    val desired = LinkedHashSet<ChunkPos>()

    // Nearest first, so the send queue is already in the order a player wants it - and so a budget that runs
    // out spends what it had on the ground closest to them.
    val offsets = (-radius..radius).flatMap { dy -> (-radius..radius).map { dx -> dx to dy } }
      .sortedBy { (dx, dy) -> dx * dx + dy * dy }

    for ((dx, dy) in offsets) {
      val column = ChunkPos(anchor.x + dx, anchor.y + dy, anchor.z)
      val isAnchorColumn = dx == 0 && dy == 0

      // The player's own column is never deferred: they must have ground under them on the first tick, whatever
      // the budget says. Everywhere else waits its turn.
      val slabs = chunkService.cachedSlabsOf(column)
        ?: if (isAnchorColumn || budget.trySpend()) chunkService.surfaceSlabsOf(column) else null

      if (slabs == null) continue

      for (z in slabs) {
        desired.add(chunkService.normalise(ChunkPos(column.x, column.y, z)))
      }

      for (z in (anchor.z - 1)..(anchor.z + 1)) {
        desired.add(chunkService.normalise(ChunkPos(column.x, column.y, z)))
      }
    }

    return desired
  }

  /**
   * The normalised horizontal columns the view covers, ignoring vertical slabs.
   *
   * Exists to tell "outside the view" apart from "inside the view but not costed yet", which the desired set
   * alone cannot say. Normalised, so a view straddling the world seam does not read its own far half as out of
   * range.
   */
  private fun viewColumnsAround(anchor: ChunkPos): Set<Pair<Int, Int>> {
    val radius = settings.viewRadiusChunks
    val columns = HashSet<Pair<Int, Int>>((2 * radius + 1) * (2 * radius + 1))

    for (dy in -radius..radius) {
      for (dx in -radius..radius) {
        val column = chunkService.normalise(ChunkPos(anchor.x + dx, anchor.y + dy, anchor.z))
        columns.add(column.x to column.y)
      }
    }

    return columns
  }

  // ---------------------------------------------------------------- step 3

  private fun broadcastChanges() {
    val changes = chunkService.drainChanges()
    if (changes.isEmpty()) return

    for (change in changes) {
      val holders = subscriptions.subscribersOf(change.chunk)
      if (holders.isEmpty()) continue

      val patchBytes = change.edits.size * ChunkPatchCodec.BYTES_PER_EDIT
      val snapshot = chunkService.encodedOf(change.chunk)

      // Past the point where the edits cost more than the whole chunk, stop describing the change and just
      // restate the result - the same trade `ChunkDelta.shouldBake` makes about storage, applied to the wire.
      if (patchBytes >= snapshot.payload.size) {
        val message = chunkService.dataMessageFor(change.chunk)
        val sent = fanOut.fanOut(holders.toList(), message)

        LOG.debug {
          "Chunk ${change.chunk} rev ${change.toRevision}: ${change.edits.size} edits would cost " +
              "$patchBytes B, sent the ${snapshot.payload.size} B snapshot to $sent clients instead"
        }
        continue
      }

      val patch = ChunkPatchSMSG.of(
        chunk = change.chunk,
        fromRevision = change.fromRevision,
        toRevision = change.toRevision,
        edits = change.edits
      )

      val sent = fanOut.fanOut(holders.toList(), patch)

      LOG.debug {
        "Chunk ${change.chunk} rev ${change.fromRevision}->${change.toRevision}: " +
            "${change.edits.size} edits, ${patch.edits.size} B to $sent clients"
      }
    }
  }

  // ---------------------------------------------------------------- step 4

  private fun serveRequests() {
    for (request in inbox.drainRequests()) {
      val budget = tokens.getOrPut(request.accountId) { settings.requestBurst }
      var spent = 0

      for (chunk in request.chunks) {
        val normalised = chunkService.normalise(chunk)

        // The gate. A position the client was never offered is not served - which is what stops a pull
        // transport from being a way to walk the whole map, or to make the server generate arbitrary
        // terrain, without needing any separate notion of a "legal" coordinate.
        if (!subscriptions.isAnnouncedTo(request.accountId, normalised)) {
          LOG.debug { "Account ${request.accountId} asked for $normalised, which it was not offered" }
          continue
        }

        if (spent >= budget) {
          LOG.debug { "Account ${request.accountId} is out of request tokens; dropping the rest" }
          break
        }

        queued.getOrPut(request.accountId) { LinkedHashSet() }.add(normalised)
        spent++
      }

      tokens[request.accountId] = budget - spent
    }

    tokens.replaceAll { _, remaining ->
      minOf(settings.requestBurst, remaining + settings.requestRefillPerTick)
    }

    sendQueued()
  }

  private fun sendQueued() {
    if (queued.isEmpty()) return

    val emptied = ArrayList<Long>()

    for ((accountId, pending) in queued) {
      var sent = 0
      val iterator = pending.iterator()

      while (iterator.hasNext() && sent < settings.chunksPerTickPerPlayer) {
        val chunk = iterator.next()
        iterator.remove()

        // It may have been withdrawn between the request and now.
        if (!subscriptions.isAnnouncedTo(accountId, chunk)) continue

        if (push(accountId, chunk)) sent++
      }

      if (pending.isEmpty()) emptied.add(accountId)
    }

    emptied.forEach { queued.remove(it) }
  }

  /** Puts a chunk at the head of an account's send queue, so it goes out before anything already waiting. */
  private fun queueFirst(accountId: Long, chunk: ChunkPos) {
    val pending = queued[accountId]

    if (pending == null) {
      queued[accountId] = linkedSetOf(chunk)
      return
    }

    // A LinkedHashSet keeps insertion order and re-adding an existing element does not move it, so the only
    // way to put something first is to rebuild. Once per manifest, over at most a view volume.
    val reordered = LinkedHashSet<ChunkPos>(pending.size + 1)
    reordered.add(chunk)
    reordered.addAll(pending)

    queued[accountId] = reordered
  }

  /**
   * Sends one chunk payload and records that the client holds it.
   *
   * `markSent` is what makes the client a patch recipient, so it must happen only when the bytes actually
   * went out. A write skipped because the channel was full leaves the chunk un-sent, and the next manifest
   * offers it again - which is the whole reason a skip is safe.
   */
  private fun push(accountId: Long, chunk: ChunkPos): Boolean {
    val message = chunkService.dataMessageFor(chunk)

    if (!fanOut.sendTo(accountId, message)) return false

    subscriptions.markSent(accountId, chunk)
    return true
  }

  private fun forget(accountId: Long) {
    subscriptions.forget(accountId)
    inbox.forget(accountId)
    queued.remove(accountId)
    tokens.remove(accountId)
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
