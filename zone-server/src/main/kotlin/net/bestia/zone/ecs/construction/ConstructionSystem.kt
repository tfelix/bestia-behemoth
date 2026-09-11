package net.bestia.zone.ecs.construction

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.prop.PlayerStructureIdentity
import net.bestia.zone.ecs.prop.PropPose
import net.bestia.zone.ecs.prop.PropVitality
import net.bestia.zone.ecs.prop.StaticSync
import net.bestia.zone.ecs.prop.StaticVisual
import net.bestia.zone.ecs.prop.WorldObjectIdentity
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.PlayerStructureRegistry
import net.bestia.zone.world.prop.PlayerStructureService
import net.bestia.zone.world.prop.PropKindRegistry
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent
import kotlin.math.roundToInt

/**
 * Puts work into every construction site somebody is standing at, and finishes the ones that are done.
 *
 * `@Order(45)`, beside [net.bestia.zone.ecs.crafting.CraftingSystem] because it is the same kind of thing, and
 * **before** `WorldObjectResidencySystem` (46) because finishing a site raises a static prop: the residency
 * drain is what announces the new column, and it has to run after this in the same tick rather than a tick
 * later.
 *
 * ### Progress is work, not time
 *
 * Unlike a cast or a craft, nothing here advances on its own. A site with no builder holds exactly where it
 * is - which is why [ConstructionSite] is a [net.bestia.zone.ecs.core.Countdown] that this system counts down
 * by hand, and why its message carries an `active` flag the client needs in order to know whether to keep
 * animating.
 */
@SpringComponent
@Order(45)
class ConstructionSystem(
  private val structures: PlayerStructureService,
  private val registry: PlayerStructureRegistry,
  private val propKinds: PropKindRegistry
) : System {

  override val reads: ComponentClassSet = setOf(Position::class, Dead::class)

  /**
   * The last five are what [PlayerStructureService.completeConstruction] puts on the *static prop* it raises,
   * through `WorldObjectResidencyService`. Declared because the scheduler looks at nothing else to decide what
   * may run alongside what, and a system that quietly writes a component it never named conflicts with nobody.
   */
  override val writes: ComponentClassSet = setOf(
    ConstructionSite::class,
    Building::class,
    Health::class,
    PropPose::class,
    StaticVisual::class,
    PropVitality::class,
    WorldObjectIdentity::class,
    StaticSync::class,
    PlayerStructureIdentity::class
  )

  override fun update(world: World, deltaTime: Float) {
    val workersPerSite = HashMap<EntityId, Int>()
    var abandoned: MutableList<EntityId>? = null

    world.query(Building::class).each { workerId ->
      val siteId = get<Building>().siteEntityId

      if (canWorkOn(world, workerId, siteId)) {
        workersPerSite.merge(siteId, 1, Int::plus)
      } else {
        (abandoned ?: mutableListOf<EntityId>().also { abandoned = it }).add(workerId)
      }
    }

    // A builder who walked off, died, or outlived their site simply stops. The site keeps what they put in.
    abandoned?.forEach { world.remove(it, Building::class) }

    // Collected first so completing one does not mutate what is being iterated.
    var completed: MutableList<Pair<EntityId, ConstructionSite>>? = null

    world.query(ConstructionSite::class).each { siteId ->
      val site = get<ConstructionSite>()
      val workers = workersPerSite[siteId] ?: 0

      site.active = workers > 0
      if (workers == 0) return@each

      // Linear in the number of builders: two people raise a workbench in half the time. The per-worker rate
      // is where a skill or a buff will eventually multiply.
      site.work(deltaTime * workers)
      rampHealth(world, siteId, site)

      if (site.hasElapsed()) {
        (completed ?: mutableListOf<Pair<EntityId, ConstructionSite>>().also { completed = it }).add(siteId to site)
      } else if (site.isPersistDue) {
        registry.updateProgress(site.structureId, site.remainingSeconds)
        site.markPersisted()
      }
    }

    completed?.forEach { (siteId, site) ->
      LOG.debug { "Construction of structure ${site.structureId} (${site.kind}) finished" }
      structures.completeConstruction(world, siteId, site)
    }
  }

  /**
   * Health rises with the work, so a site is trivially wrecked on the first day and solid on the last.
   *
   * Damage already taken is carried across rather than healed away: `CurMax` clamps `current` down with `max`
   * but never raises it, so assigning the new ceiling alone would leave a finished workbench on one hit point.
   */
  private fun rampHealth(world: World, siteId: EntityId, site: ConstructionSite) {
    val health = world.get(siteId, Health::class) ?: return

    val finalHp = propKinds.of(site.kind).maxHp
    val ceiling = (ConstructionSite.START_HP + (finalHp - ConstructionSite.START_HP) * site.progress)
      .roundToInt()
      .coerceAtLeast(ConstructionSite.START_HP)

    if (ceiling == health.max) return

    val missing = health.max - health.current
    health.max = ceiling
    health.current = (ceiling - missing).coerceAtLeast(1)
  }

  private fun canWorkOn(world: World, workerId: EntityId, siteId: EntityId): Boolean {
    if (!world.isAlive(siteId) || world.get(siteId, ConstructionSite::class) == null) return false
    if (world.has(workerId, Dead::class)) return false

    val worker = world.get(workerId, Position::class)?.toVec3L() ?: return false
    val site = world.get(siteId, Position::class)?.toVec3L() ?: return false

    // The reach to build is the reach to craft at the finished thing, which is the one number `skills.yml`
    // already gives the client for aiming.
    return worker.distance(site) <= PlayerStructureService.RANGE_TILES
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
