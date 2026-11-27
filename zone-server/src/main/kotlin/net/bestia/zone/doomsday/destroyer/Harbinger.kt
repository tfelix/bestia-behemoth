package net.bestia.zone.doomsday.destroyer

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.doomsday.ForeshadowKind
import net.bestia.zone.doomsday.foreshadow.Foreshadowing
import net.bestia.zone.doomsday.persistence.StoredForeshadowing
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.anchor.AnchorPriority
import net.bestia.zone.world.anchor.SimulationAnchor
import net.bestia.zone.worldevent.EventGround
import org.springframework.stereotype.Component
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Harbinger: a Foreshadowing of the Destroyer. A lesser boss walks out near the heart of its province and marches
 * once on the nearest town. Brought down, it is held; once it razes the town, or still walks at the end, it is lost.
 */
@Component
class Harbinger(
  private val config: DestroyerConfig,
  private val fall: HarbingerFall,
  private val towns: DestroyerTowns,
  private val catalogue: BestiaCatalogue,
  private val world: WorldView,
  private val spawner: BestiaEntitySpawner,
  private val statusEffects: StatusEffectService,
  private val provinces: ProvinceService,
  private val eventGround: EventGround,
  private val worldService: WorldService,
) : Foreshadowing {

  override val kind = ForeshadowKind.HARBINGER

  /** Not stored: after a restart it walks out again, unless the season is decided. */
  @Volatile
  private var live: EntityId? = null

  override fun step(season: StoredForeshadowing, realSeconds: Double) {
    if (isDecided(season)) {
      return
    }
    if (fall.take()) {
      live = null
      season.held++
      LOG.info { "The Harbinger was brought down" }
      return
    }

    val id = live?.takeIf { world.read { isAlive(it) } } ?: walkOut(season)?.also { live = it } ?: return
    world.modify(id) {
      val metres = worldService.config.voxelSize
      val position = get(it, Position::class) ?: return@modify
      val x = position.x * metres
      val y = position.y * metres
      val target = towns.nearest(x, y, emptyList()) ?: return@modify

      if (towns.metresTo(target, x, y) <= config.arriveMetres) {
        towns.raze(this, target)
        destroy(it)
        live = null
        season.lost++
        LOG.warn { "The Harbinger razed $target and is gone" }
        return@modify
      }
      val home = towns.groundAt(target) ?: return@modify
      get(it, AiAgent::class)?.memory?.let { memory ->
        memory.set(BestiaDomain.HOME_POSITION, home, Blackboard.PERMANENT)
        memory.set(BestiaDomain.WANDER_RADIUS, config.wanderVoxels, Blackboard.PERMANENT)
      }
    }
  }

  override fun contained(season: StoredForeshadowing): Boolean {
    return season.held > 0
  }

  override fun end(season: StoredForeshadowing, contained: Boolean) {
    val id = live ?: return
    live = null
    world.modify(id) { destroy(it) }
  }

  private fun isDecided(season: StoredForeshadowing): Boolean {
    return season.held + season.lost > 0
  }

  /** A lesser boss, out of sight of the province's heart, outside the sanctuaries. */
  private fun walkOut(season: StoredForeshadowing): EntityId? {
    val random = Random(season.provinceIndex.toLong())
    val species = catalogue.all().filter { it.boss && !it.worldBoss && !it.eventOnly }.randomOrNull(random) ?: return null
    val at = (0 until SPOT_TRIES).firstNotNullOfOrNull {
      val angle = random.nextDouble(0.0, 2 * Math.PI)
      val x = season.x + cos(angle) * WALK_OUT_METRES
      val y = season.y + sin(angle) * WALK_OUT_METRES
      if (provinces.isSanctuaryAt(x, y)) null else eventGround.grounded(x, y)
    } ?: return null

    LOG.warn { "The Harbinger, a ${species.identifier}, walks out" }
    return world.read {
      val id = spawner.spawnMob(this, species.id, at, persistent = false, homePosition = at)
      add(id, EventCreature(HarbingerFall.EVENT, 0))
      add(id, SimulationAnchor(priority = AnchorPriority.TRAVELLER, atRest = AnchorPriority.TRAVELLER))
      statusEffects.applyEffect(this, id, StatusEffectId.DESTROYER, level = 1)
      id
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    const val WALK_OUT_METRES = 1_500.0
    const val SPOT_TRIES = 8
  }
}
