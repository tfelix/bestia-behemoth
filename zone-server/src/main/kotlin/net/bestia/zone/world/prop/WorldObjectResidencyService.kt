package net.bestia.zone.world.prop

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.ecs.AoiLayer
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.prop.PropPose
import net.bestia.zone.ecs.prop.PropVitality
import net.bestia.zone.ecs.prop.StaticSync
import net.bestia.zone.ecs.prop.StaticVisual
import net.bestia.zone.ecs.prop.WorldObjectIdentity
import net.bestia.zone.socket.ChunkFanOut
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkStaticEntitiesSMSG
import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Which static entities exist right now, and which chunk each of them belongs to.
 *
 * ### The rule
 *
 * A chunk column's static entities exist while at least one client holds terrain for that column, and not
 * otherwise. On a 128 km world there are on the order of a million tree props; the ECS holds the few thousand
 * standing on ground somebody can see.
 *
 * ### Nothing here is a cache
 *
 * A column that leaves every view is *destroyed*, and when it comes back it is built again from
 * `propsIn` plus whatever divergence is stored - not restored from anything held here. So there is no cached
 * copy that can go stale against the generator, and the server is always re-sending rather than trusting that
 * a client remembers.
 *
 * **What makes that safe is that a prop's entity id is ephemeral.** A re-materialised tree gets a fresh
 * snowflake. Nothing may hold a long-lived reference to one, and anything that has to survive the column
 * leaving the view is keyed on `WorldObjectIdentity.propId` instead - which is why [WorldObjectDivergence] is
 * keyed on the propId and not on an entity id. Keyed on the id, re-sending would silently orphan every row.
 *
 * ### Refcounted by column, not by chunk
 *
 * [ChunkSubscriptionService] addresses a *slab*, so a column with three subscribed slabs fires the
 * first-subscriber callback three times and the last-subscriber callback three times. A static entity stands on
 * the surface and belongs to the column, so this counts holders per column and materialises on the first and
 * releases on the last. Without that, walking through a cave would delete the trees overhead.
 *
 * ### Tick thread only
 *
 * Every method mutates the ECS or the interest index. The subscription callbacks fire while systems are
 * iterating, so they only enqueue - [WorldObjectResidencySystem] drains under a budget.
 */
@Service
class WorldObjectResidencyService(
  private val sources: List<WorldObjectSource>,
  private val kinds: PropKindRegistry,
  private val aoi: EntityAOIService,
  private val fanOut: ChunkFanOut,
  private val worldService: WorldService,
  private val divergence: WorldObjectDivergenceRegistry,
  subscriptions: ChunkSubscriptionService
) {

  /** Voxels per chunk edge, for turning a world position into a chunk-local one. */
  private val chunkSize: Int get() = worldService.config.chunkSize

  /**
   * The lattice version a freshly materialised prop is stamped with - `pipelineVersion`, not
   * `ChunkMaterializer.VERSION` alone, because a pure params retune (`VegetationParams.cellSize`, the POI
   * catalogue) never bumps that hand-incremented counter but does fold into this one via
   * `WorldParams.chunkTierVersion` and every stage's `paramsVersion`. See [WorldObjectDivergence]'s KDoc.
   */
  private val latticeVersion: Long get() = worldService.record.pipelineVersion

  /** Packed `(x, y)` chunk column -> the entities standing in it. */
  private val resident = HashMap<Long, LongArray>()

  /** Packed column -> how many subscribed slabs of it are held. See the class note. */
  private val holders = HashMap<Long, Int>()

  private val pendingLoad = LinkedHashSet<Long>()
  private val pendingRelease = LinkedHashSet<Long>()

  /**
   * Column -> accounts that hold its terrain and have not been told what stands on it.
   *
   * Keyed by column so a batch is encoded **once** however many accounts are waiting for it, which is
   * `ChunkFanOut`'s whole contract. Entries survive a tick: a column can be held before it is materialised -
   * the terrain goes out at order 45 and the entities appear at 46 - so a waiter whose column is not resident
   * yet simply waits for the next drain.
   */
  private val awaitingBatch = HashMap<Long, MutableSet<Long>>()

  init {
    subscriptions.onFirstSubscriber { chunk -> hold(chunk) }
    subscriptions.onLastSubscriber { chunk -> release(chunk) }

    // Every recipient, not only the first: the second player into a wood gets no first-subscriber callback
    // and still has to be told about the trees.
    subscriptions.onChunkSent { accountId, chunk ->
      awaitingBatch.getOrPut(columnOf(chunk)) { HashSet() }.add(accountId)
    }
  }

  val residentColumns get() = resident.size
  val residentEntities get() = resident.values.sumOf { it.size }
  val pending get() = pendingLoad.size + pendingRelease.size

  /**
   * Columns whose holders have the terrain but have not yet been told what stands on it.
   *
   * Separate from [pending] because the two can move independently, and the case where they do is the ordinary
   * one: the second player into a wood is served a column somebody else already materialised, so nothing is
   * queued to load or release and there is still a batch owed. A drain gated on [pending] alone never flushes it.
   */
  val awaitingBatches get() = awaitingBatch.size

  private fun hold(chunk: ChunkPos) {
    val column = columnOf(chunk)
    val count = (holders[column] ?: 0) + 1
    holders[column] = count

    if (count == 1) {
      // A column can be released and re-held inside one tick - a teleport does exactly that, since a `reset`
      // manifest withdraws everything before re-announcing. Cancelling the release is not merely an
      // optimisation: releasing then loading would destroy and re-create every entity in the column and hand
      // every client a new set of ids for the same trees.
      if (!pendingRelease.remove(column)) pendingLoad.add(column)
    }
  }

  private fun release(chunk: ChunkPos) {
    val column = columnOf(chunk)
    val count = (holders[column] ?: 0) - 1

    if (count <= 0) {
      holders.remove(column)
      if (!pendingLoad.remove(column)) pendingRelease.add(column)
    } else {
      holders[column] = count
    }
  }

  /**
   * Materialises up to [budget] queued columns and releases up to [budget] more.
   *
   * Budgeted for the reason `ChunkStreamConfig.chunksPerTickPerPlayer` is: a login queues a whole view volume
   * at once, and `World.destroyNow` walks *every* component store per entity - about fifty of them - so an
   * unbudgeted mass release is measured in whole ticks.
   *
   * @return how many columns were materialised and released
   */
  fun drain(world: World, budget: Int): Pair<Int, Int> {
    var loaded = 0
    while (loaded < budget && pendingLoad.isNotEmpty()) {
      val column = pendingLoad.first()
      pendingLoad.remove(column)
      materialise(world, column)
      loaded++
    }

    var released = 0
    while (released < budget && pendingRelease.isNotEmpty()) {
      val column = pendingRelease.first()
      pendingRelease.remove(column)
      dematerialise(world, column)
      released++
    }

    // After both, so a column materialised this tick is announced in the same tick its terrain was, and one
    // released this tick is not announced at all.
    //
    // **Deferred, because `materialise` above cannot have attached its components yet.** This drain runs from
    // inside `WorldObjectResidencySystem.update`, so `World.iterating` is set and every `add` in `materialise`
    // is queued rather than applied - and [flushBatches] reads `PropPose` and `StaticVisual` back off the
    // entities to build the batch. Reading them in this call therefore found nothing on any of them and sent
    // an *empty* batch for every column in the world, which a client cannot tell from ground that genuinely
    // has nothing on it. `defer` appends to the same queue the adds went into, so it runs after them; off the
    // tick thread (tests calling `drain` directly) it simply runs now.
    world.defer { flushBatches(world) }

    return loaded to released
  }

  /**
   * Tells each waiting account what stands in the columns it now holds.
   *
   * One encode per column regardless of how many accounts are waiting - thirty players walking into the same
   * wood cost one serialisation between them, which is the reason this goes through [ChunkFanOut] rather than
   * the ordinary per-recipient send path.
   */
  private fun flushBatches(world: World) {
    if (awaitingBatch.isEmpty()) return

    val columns = awaitingBatch.keys.toList()

    for (column in columns) {
      val ids = resident[column] ?: continue
      val accounts = awaitingBatch.remove(column) ?: continue
      if (accounts.isEmpty()) continue

      val chunkX = unpackX(column)
      val chunkY = unpackY(column)

      // An empty column still gets a message. It is twelve bytes and it is what tells a client "this ground
      // has nothing on it" rather than "the batch has not arrived yet", which are different states for
      // anything that wants to know whether it can start drawing.
      val entries = ArrayList<ChunkStaticEntitiesSMSG.Entry>(ids.size)

      world.read {
        for (id in ids) {
          val pose = get(id, PropPose::class) ?: continue
          val visual = get(id, StaticVisual::class) ?: continue

          entries.add(
            ChunkStaticEntitiesSMSG.Entry(
              entityId = id,
              kind = visual.kind,
              variant = visual.variant,
              localX = (pose.position.x - chunkX.toLong() * chunkSize).toInt(),
              localY = (pose.position.y - chunkY.toLong() * chunkSize).toInt(),
              z = pose.position.z.toInt(),
              heightDm = visual.heightDm,
              yawCentiradians = Math.round(pose.yaw * 100f)
            )
          )
        }
      }

      fanOut.fanOut(accounts, ChunkStaticEntitiesSMSG(ChunkPos(chunkX, chunkY, 0), entries))
    }
  }

  /** The entities of one column, for the sync channel to batch. Empty when the column is not resident. */
  fun entitiesIn(chunkX: Int, chunkY: Int): LongArray = resident[pack(chunkX, chunkY)] ?: EMPTY

  private fun materialise(world: World, column: Long) {
    if (resident.containsKey(column)) return

    val chunk = ChunkPos(unpackX(column), unpackY(column), 0)
    val sites = sources.flatMap { it.sitesIn(chunk) }.filter { shouldEmit(it.propId) }

    if (sites.isEmpty()) {
      // Recorded as resident anyway, so a chunk with no trees on it is not re-asked on every tick it is held.
      resident[column] = EMPTY
      return
    }

    val ids = LongArray(sites.size)

    sites.forEachIndexed { i, site ->
      val spec = kinds.of(site.kind)
      val id = world.createEntity { id ->
        add(id, PropPose(site.position, site.yaw))
        add(id, StaticVisual(site.kind, site.variant % spec.variants, site.heightDm))
        add(id, PropVitality(spec.maxHp))
        add(id, WorldObjectIdentity(site.propId, latticeVersion))
        add(id, StaticSync)
      }

      // Straight into the interest index rather than through the dirty-Position path, which is the only other
      // way in: `ZoneEngine` inserts from its dirty-`Position` loop, and a static entity is deliberately not in
      // the `Position` store. Being *findable* by an area query and being *synced* per component are
      // independent, and a fireball has to find a tree.
      aoi.setEntityPosition(id, site.position, AoiLayer.STATIC)
      ids[i] = id
    }

    resident[column] = ids
  }

  /**
   * Whether a generated site should still stand: no recorded divergence, or a temporary one (a felled tree
   * with a `regrowSeconds`) whose regrowth clock has passed - evicted right here, since a chunk becoming
   * resident is exactly the moment anyone would notice it grew back. A terminal divergence (`resumeAt ==
   * null` - a claimed POI, a mined-out crystal) never emits again.
   */
  private fun shouldEmit(propId: Long): Boolean {
    val entry = divergence.of(propId) ?: return true
    val resumeAt = entry.resumeAt ?: return false
    if (resumeAt.isAfter(Instant.now())) return false

    divergence.evictRegrown(propId)
    return true
  }

  private fun dematerialise(world: World, column: Long) {
    // A client that lost the terrain will discard the column's contents anyway, so a batch for it now would
    // describe entities that are about to stop existing.
    awaitingBatch.remove(column)

    val ids = resident.remove(column) ?: return

    for (id in ids) {
      aoi.removeEntityPosition(id)
      world.destroy(id)
    }
  }

  private fun columnOf(chunk: ChunkPos) = pack(chunk.x, chunk.y)

  private companion object {
    val EMPTY = LongArray(0)

    fun pack(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    fun unpackX(packed: Long): Int = (packed shr 32).toInt()
    fun unpackY(packed: Long): Int = packed.toInt()
  }
}
