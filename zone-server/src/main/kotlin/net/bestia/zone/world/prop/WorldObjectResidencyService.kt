package net.bestia.zone.world.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.ecs.AoiLayer
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.prop.PropPose
import net.bestia.zone.ecs.prop.PropVitality
import net.bestia.zone.ecs.prop.StaticSync
import net.bestia.zone.ecs.prop.StaticVisual
import net.bestia.zone.ecs.prop.WorldObjectIdentity
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.springframework.stereotype.Service

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
 * leaving the view is keyed on `WorldObjectIdentity.propId` instead - which is why the delta table is keyed on
 * the lattice cell and not on an entity id. Keyed on the id, re-sending would silently orphan every row.
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
  subscriptions: ChunkSubscriptionService
) {

  /** Packed `(x, y)` chunk column -> the entities standing in it. */
  private val resident = HashMap<Long, LongArray>()

  /** Packed column -> how many subscribed slabs of it are held. See the class note. */
  private val holders = HashMap<Long, Int>()

  private val pendingLoad = LinkedHashSet<Long>()
  private val pendingRelease = LinkedHashSet<Long>()

  init {
    subscriptions.onFirstSubscriber { chunk -> hold(chunk) }
    subscriptions.onLastSubscriber { chunk -> release(chunk) }
  }

  val residentColumns get() = resident.size
  val residentEntities get() = resident.values.sumOf { it.size }
  val pending get() = pendingLoad.size + pendingRelease.size

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

    return loaded to released
  }

  /** The entities of one column, for the sync channel to batch. Empty when the column is not resident. */
  fun entitiesIn(chunkX: Int, chunkY: Int): LongArray = resident[pack(chunkX, chunkY)] ?: EMPTY

  private fun materialise(world: World, column: Long) {
    if (resident.containsKey(column)) return

    val chunk = ChunkPos(unpackX(column), unpackY(column), 0)
    val sites = sources.flatMap { it.sitesIn(chunk) }

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
        add(id, WorldObjectIdentity(site.propId, LATTICE_VERSION_UNSET))
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

  private fun dematerialise(world: World, column: Long) {
    val ids = resident.remove(column) ?: return

    for (id in ids) {
      aoi.removeEntityPosition(id)
      world.destroy(id)
    }
  }

  private fun columnOf(chunk: ChunkPos) = pack(chunk.x, chunk.y)

  private companion object {
    val EMPTY = LongArray(0)

    /**
     * Placeholder until the delta table exists.
     *
     * Zero rather than a computed digest on purpose: nothing reads a stored divergence yet, and a version
     * number that looked real would invite somebody to persist against it before there is anything to
     * invalidate. See the plan's Phase 3.
     */
    const val LATTICE_VERSION_UNSET = 0L

    fun pack(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    fun unpackX(packed: Long): Int = (packed shr 32).toInt()
    fun unpackY(packed: Long): Int = packed.toInt()
  }
}
