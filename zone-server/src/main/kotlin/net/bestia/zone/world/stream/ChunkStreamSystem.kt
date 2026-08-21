package net.bestia.zone.world.stream

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.GroundHeight
import net.bestia.zone.ecs.movement.Grounded
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.socket.ChunkFanOut
import net.bestia.zone.util.EntityId
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
  private val settings: ChunkStreamConfig,
  private val groundHeight: GroundHeight
) : System {

  override val reads: ComponentClassSet = setOf(Account::class, ActivePlayer::class)

  /**
   * [Position] and [Path] are written, not just read, because a teleport moves a player.
   *
   * Declaring them matters beyond documentation: the scheduler runs systems in parallel unless their read/write
   * sets conflict, so leaving `Position` in `reads` alone would let this run concurrently with `MoveSystem`,
   * which writes it. `@Order` fixes the sequence only among systems that already conflict.
   */
  override val writes: ComponentClassSet = setOf(Position::class, Path::class, Grounded::class)

  /**
   * Gives the derived structures their residency, which nothing used to.
   *
   * `DerivedStore` builds on demand and its production callers all guard on `isTracked` first, so nothing ever
   * put a chunk into it: walkability, line of sight and ground height answered "unknown" for the whole life of
   * the process, and movement fell back to whatever height the client claimed. The subscription set is the
   * right scope for it - a chunk somebody is holding is a chunk something may walk in - and these two hooks
   * are the one place that already knows when a chunk enters and leaves it, refcounted across login, walking,
   * a `reset` manifest, master deselection and disconnect alike.
   *
   * Wired here rather than in [ChunkService] because this is where both beans already meet, and because the
   * lambdas must not touch the world until they fire: `derived()` forces world generation, and at bean
   * construction time there is no world. They can only fire from inside [update], which is behind the
   * `isReady` guard.
   *
   * Marking only, as both hooks require - `track` queues a build and returns, and the budget in step five pays
   * for it.
   */
  init {
    subscriptions.onFirstSubscriber { chunkService.derived().track(it) }
    subscriptions.onLastSubscriber { chunkService.derived().forget(it) }
  }

  /** Chunks a client has asked for and not yet been sent, nearest first. */
  private val queued = HashMap<Long, LinkedHashSet<ChunkPos>>()

  /** Token buckets, so a client cannot ask faster than it is served. */
  private val tokens = HashMap<Long, Int>()

  override fun update(world: World, deltaTime: Float) {
    if (!chunkService.isReady) return

    applyCarves()

    // Before the subscriptions, so a teleport and the manifest that answers it happen in the same tick. The
    // other order would offer the player a view volume around where they used to be and correct it one tick
    // later, which is a visible flash of the wrong terrain.
    groundNewcomers(world)
    applyTeleports(world)

    updateSubscriptions(world)
    broadcastChanges()
    serveRequests()
    chunkService.rebuildDerived()
  }

  // ---------------------------------------------------------------- step 1

  private fun applyCarves() {
    val carves = inbox.drainCarves()
    if (carves.isEmpty()) return

    for (carve in carves) {
      if (carve.radius < CarveBrush.MIN_RADIUS) {
        LOG.warn {
          "Account ${carve.accountId} asked for a bore radius of ${carve.radius}, below the " +
              "${CarveBrush.MIN_RADIUS} the client can render"
        }
        continue
      }

      // Centred on the voxel's centre rather than its corner, so a brush aimed at a voxel is symmetric about
      // it. Localisation and the world seam are `ChunkService.carve`'s business - a brush is a shape in world
      // space and can reach into several chunks at once.
      val brush = CarveBrush.sphere(
        carve.x + 0.5,
        carve.y + 0.5,
        carve.z + 0.5,
        carve.radius
      )

      val result = chunkService.carve(brush)

      LOG.debug {
        "Account ${carve.accountId} carved r=${carve.radius} at (${carve.x},${carve.y},${carve.z}): " +
            "${result.voxels.size} voxels across ${result.chunks.size} chunks"
      }
    }
  }

  /**
   * Puts every entity that has never been reconciled with the terrain onto the ground.
   *
   * This is the tick-thread half of a problem the request thread cannot solve: something has to name a `z` when
   * a master is created or a script places an entity, and the ground elevation is only knowable here. The
   * consequence of naming it too low is severe - the entity spawns *inside* the terrain, where every surrounding
   * chunk is uniform rock, encodes to twelve bytes, meshes to no surface and renders as a black screen. It looks
   * exactly like the terrain failing to load.
   *
   * Runs before the subscriptions for the same reason the teleport does: the manifest that follows should describe
   * where the player actually is, not where they were for the first tick of their session.
   *
   * @see Grounded for why this is a marker rather than a "snap anything below the surface" rule
   */
  private fun groundNewcomers(world: World) {
    val ungrounded = ArrayList<EntityId>()

    world.query(Position::class).each { id ->
      if (world.get(id, Grounded::class) == null) ungrounded.add(id)
    }

    for (entityId in ungrounded) {
      val position = world.get(entityId, Position::class) ?: continue
      val z = groundHeight.standingZAt(position.toVec3L())

      if (z == null) {
        // Off the grid. Marked anyway: retrying every tick would ask the same unanswerable question sixty times
        // a second, and a position outside the world is a different bug from a position at the wrong height.
        world.add(entityId, Grounded)
        continue
      }

      if (z != position.z) {
        LOG.info { "Grounded entity $entityId at (${position.x},${position.y}): z ${position.z} -> $z" }
        position.z = z
      }

      world.add(entityId, Grounded)
    }
  }

  /**
   * Puts a player down somewhere else, on the ground rather than at whatever elevation they were.
   *
   * Here rather than in the chat command for two reasons that both come down to thread ownership: the ground
   * elevation is [ChunkService]'s to answer and it has one owning thread, and the position write wants to land
   * in the same tick as the manifest that follows it.
   *
   * Any [Path] is discarded. A player who was walking has a queue of waypoints back where they came from, and
   * `MoveSystem` would otherwise spend the next few seconds dragging them home across the world.
   */
  private fun applyTeleports(world: World) {
    val teleports = inbox.drainTeleports()
    if (teleports.isEmpty()) return

    val entities = HashMap<Long, EntityId>()
    world.query(Position::class, Account::class, ActivePlayer::class).each { id ->
      entities[get<Account>().accountId] = id
    }

    for (teleport in teleports) {
      val entityId = entities[teleport.accountId]
      if (entityId == null) {
        LOG.warn { "Account ${teleport.accountId} asked to move but has no active entity" }
        continue
      }

      val position = world.get(entityId, Position::class) ?: continue

      // Shared with MoveSystem's per-step snap, so a teleport and a walk agree about where the ground is.
      val z = groundHeight.standingZAt(Vec3L(teleport.x, teleport.y, position.z))
      if (z == null) {
        LOG.warn { "Account ${teleport.accountId} asked to move to (${teleport.x},${teleport.y}), off the grid" }
        continue
      }

      position.x = teleport.x
      position.y = teleport.y
      position.z = z

      world.remove(entityId, Path::class)

      LOG.info { "Moved account ${teleport.accountId} to (${teleport.x},${teleport.y},$z)" }
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
   *   session and only then. A teleport gets an amendment: the old view's columns fall outside the radius and
   *   are withdrawn by the ordinary rule, whereas a replacement is bounded by this tick's slab budget and would
   *   have the client discard terrain the manifest had no room to re-list.
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

    // Recorded only once the bytes are away. An account credited with an offer it never received asks for
    // nothing, and the diff above is computed against what was announced - so those chunks are never mentioned
    // again and the client holds a hole for the rest of the session.
    if (!fanOut.sendTo(accountId, ChunkManifestSMSG(reset = reset, added = refs, removed = removed))) {
      LOG.debug { "Manifest for $accountId could not be written; leaving its subscription untouched" }
      return
    }

    subscriptions.applyManifest(accountId, added, removed, reset)

    // Withdrawn chunks must also leave the send queue, or a client would be handed terrain it has just been
    // told to forget.
    queued[accountId]?.removeAll(removed.toSet())

    // The chunk under the player's feet goes out unasked, at the head of the queue. Everything else waits to
    // be requested, but making the player wait a round trip for the ground they are standing on is the one
    // case where the saving is not worth it.
    //
    // Gated on not having sent it rather than on `reset`, which only ever means the first manifest of a
    // session: a teleport wants this exactly as much as a login does and never sets that flag.
    //
    // Queued rather than written here, and it still leaves in this same tick - step four drains the queue.
    // Writing it here instead would mark the client as holding the chunk *before* step three broadcasts this
    // tick's edits, so an edit to this very chunk in step one would then be described by a patch the client
    // must reject: the exact race the three-before-four ordering exists to avoid. Going through the queue
    // costs nothing and keeps that argument free of special cases.
    val ground = chunkService.normalise(anchor)
    if (ground !in subscriptions.sentTo(accountId)) queueFirst(accountId, ground)

    LOG.debug {
      "Manifest for $accountId at $anchor: ${added.size} added, ${removed.size} removed, reset=$reset"
    }
  }

  /**
   * The chunks a player at [anchor] should hold.
   *
   * Horizontally a square of `viewRadiusChunks` around them. Vertically, the slabs that hold a *surface* in
   * each column - the generated terrain, the sea, cave passages, and whatever anybody has dug into, which
   * [ChunkService.surfaceSlabsOf] answers as one set - clipped to `viewRadiusChunksVertical` around the
   * player's own slab, plus that slab itself:
   *
   * ```
   * kept(column)  = (surfaces(column) ∩ [anchor.z - v, anchor.z + v]) ∪ { anchor.z }
   * slabs(column) = kept ∪ { z - 1 | z ∈ kept, z - 1 ∈ surfaces(column) }
   * ```
   *
   * ### Why the surface term is a set and not a span
   *
   * A chunk is 256 m tall, so one slab almost always contains a whole column's surface - but not the
   * *seabed's* and the *sea's* at once. Deep ocean puts them four slabs apart, and everything between is
   * solid water that the client meshes to nothing. Subscribing to the span rather than to its two ends is
   * what made a login in the ocean margin offer 726 chunks where 121 was the answer.
   *
   * ### Why it is clipped
   *
   * A view volume wants bounding in z for the same reason as in x and y, and without it a coastal column
   * offers the seabed a kilometre below a player who cannot see it. The clip is what keeps the count
   * proportional to what is on screen rather than to how deep the water happens to be.
   *
   * ### Why the player's own slab survives the clip
   *
   * They must have ground under them on the first tick even where the surface rule disagrees - standing on a
   * built platform, or in a cave, or simply at an elevation the heightfield knows nothing about. It replaces
   * an unconditional `anchor.z ± 1`, which tripled the count to buy the same guarantee.
   *
   * ### Why the clip may be exceeded by one slab downward, and why the order matters
   *
   * Both are [ChunkCoords.offeredSlabs]'s to explain. In short: a slab kept while the slab it draws its floor
   * against was clipped away offers something that draws nothing, and insertion order here becomes send order,
   * so a floor has to go out before what draws against it.
   */
  private fun desiredChunks(anchor: ChunkPos, budget: Budget): Set<ChunkPos> {
    val radius = settings.viewRadiusChunks
    val vertical = settings.viewRadiusChunksVertical
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

      ChunkCoords.offeredSlabs(slabs, anchor.z, vertical)
        .forEach { desired.add(chunkService.normalise(ChunkPos(column.x, column.y, it))) }
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

      // Encoded up front rather than estimated. The patch has to be built to be sent anyway, and now that a
      // removal is one to four bytes rather than a fixed five there is no multiplication that gets this right -
      // `MAX_BYTES_PER_REMOVAL` would overstate a column of adjacent removals by nearly four times and send a
      // snapshot where a patch would comfortably have done.
      val patch = ChunkPatchSMSG.of(
        chunk = change.chunk,
        fromRevision = change.fromRevision,
        toRevision = change.toRevision,
        removals = change.removals
      )

      val snapshot = chunkService.encodedOf(change.chunk)

      // Past the point where the removals cost more than the whole chunk, stop describing the change and just
      // restate the result - the same trade `ChunkDelta.shouldBake` makes about storage, applied to the wire.
      if (patch.removals.size >= snapshot.payload.size) {
        val message = chunkService.dataMessageFor(change.chunk)
        val sent = fanOut.fanOut(holders.toList(), message)

        LOG.debug {
          "Chunk ${change.chunk} rev ${change.toRevision}: ${change.removals.size} removals cost " +
              "${patch.removals.size} B, sent the ${snapshot.payload.size} B snapshot to $sent clients instead"
        }
        continue
      }

      val sent = fanOut.fanOut(holders.toList(), patch)

      LOG.debug {
        "Chunk ${change.chunk} rev ${change.fromRevision}->${change.toRevision}: " +
            "${change.removals.size} removals, ${patch.removals.size} B to $sent clients"
      }
    }
  }

  // ---------------------------------------------------------------- step 4

  private fun serveRequests() {
    for (request in inbox.drainRequests()) {
      val budget = tokens.getOrPut(request.accountId) { settings.requestBurst }
      var spent = 0

      for ((index, chunk) in request.chunks.withIndex()) {
        val normalised = chunkService.normalise(chunk)

        // The gate. A position the client was never offered is not served - which is what stops a pull
        // transport from being a way to walk the whole map, or to make the server generate arbitrary
        // terrain, without needing any separate notion of a "legal" coordinate.
        if (!subscriptions.isAnnouncedTo(request.accountId, normalised)) {
          LOG.debug { "Account ${request.accountId} asked for $normalised, which it was not offered" }
          continue
        }

        if (spent >= budget) {
          // Deferred rather than dropped. A manifest diffs against what has been announced, so a request
          // discarded here is never re-offered and the client never asks again - which turned a rate limit that
          // only meant to slow the asking down into permanent holes in the terrain, worst right after a
          // teleport asks for a whole view volume at once.
          val deferred = request.chunks.drop(index)
          inbox.offerRequest(ChunkStreamInbox.Request(request.accountId, deferred))

          LOG.debug { "Account ${request.accountId} is out of request tokens; deferring ${deferred.size}" }
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

        // It may have been withdrawn between the request and now.
        if (!subscriptions.isAnnouncedTo(accountId, chunk)) {
          iterator.remove()
          continue
        }

        // Kept on a failed write, because nothing else would bring it back - the manifest offers what was not
        // announced, not what did not arrive. A channel that is gone or full will refuse the rest of this
        // tick's writes too, so stop rather than spend the budget finding that out chunk by chunk.
        if (!push(accountId, chunk)) break

        iterator.remove()
        sent++
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
   * went out. A skipped write leaves the chunk un-sent and in the send queue; the caller retries it on a later
   * tick. The manifest will not, because it offers what was never announced rather than what never arrived.
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
