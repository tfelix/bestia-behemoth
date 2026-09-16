package net.bestia.zone.environment.weather

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Invulnerable
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.skill.findByIdentifier
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import org.springframework.core.annotation.Order
import kotlin.math.abs
import kotlin.math.roundToInt
import org.springframework.stereotype.Component as SpringComponent

/**
 * Exposure drains stamina, and only bites health once stamina is gone. That ordering is the whole difference
 * between a mechanic and a nuisance: a player crossing a cold pass is slowed and warned, and a player who
 * ignores it dies. Damage straight to health would make a blizzard an unavoidable tax on travel.
 *
 * ### Shelter, and the one weather that ignores it
 *
 * `LocalTemperature` already damps the diurnal and weather terms under a roof, so a sheltered player simply
 * gets a milder temperature rather than a special case here. `WeatherKind.MANA_STORM` is exempt from that
 * damping at the source, which is what gives a roof a limit and `WEATHER_RESISTANCE` something to be for.
 */
@SpringComponent
@Order(85)
class EnvironmentalExposureSystem(
  private val weatherService: WeatherService,
  private val chunkService: ChunkService,
  private val config: ExposureConfig,
  private val skills: SkillRepository
) : System {

  /** Resolved by identifier, because the id in `skills.yml` is content and this is code. */
  private val resistanceId: Long? by lazy { skills.findByIdentifier(SkillId.WEATHER_RESISTANCE)?.id }

  override val schedule: Schedule get() = Schedule.EverySeconds(config.intervalSeconds)

  override val reads: ComponentClassSet = setOf(Invulnerable::class)

  override val writes: ComponentClassSet = setOf(Stamina::class, Health::class)

  private var drained = 0L
  private var hurt = 0L

  override fun update(world: World, deltaTime: Float) {
    if (!config.enabled) return

    // Before anything touches the world: reading the config is what forces it to load, and generating a world
    // inside one tick is not a thing this system should be able to cause.
    if (!chunkService.isReady) return

    val worldConfig = chunkService.config

    world.query(Position::class, Stamina::class).each { entityId ->
      // The weather is the second way health is lost, so it is the second place invulnerability is honoured.
      // Skipped whole rather than only at the health line: draining the stamina of something that cannot be
      // worn down is work with no reader.
      if (world.has(entityId, Invulnerable::class)) return@each

      val position = get<Position>()
      val stamina = get<Stamina>()

      // The entity's own altitude, not the terrain's under it, and the difference is the whole tick budget.
      // `GroundHeight` already put this entity on the ground, so asking `ChunkService.surfaceElevationAt` for
      // that ground again re-derives an answer the position already holds - and re-derives it the expensive
      // way: that call computes all 1 024 columns of a chunk on a cache miss, while this sweep visits every
      // entity with a position in whatever order the store holds them, so the hot-chunk cache thrashes and a
      // few hundred entities become a few hundred chunk-height computations back to back on `zone-tick`.
      // Measured at 1 634 ms against a 50 ms budget, which then reaches `MoveSystem` as a delta worth six
      // tiles and desyncs every walk in the zone. `ChunkNavWorldSource.place` dropped the same lookup for the
      // same reason.
      //
      // It is also the better number. What exposure wants is how high up the *entity* is, and for one on an
      // upper floor or down a shaft that is its own z rather than the column's surface.
      val ground = ChunkCoords.elevationOf(worldConfig, position.z)

      val air = weatherService.at(position.x, position.y, ground).temperature.airCelsius

      // WEATHER_RESISTANCE **widens the band** rather than reducing the damage, and that distinction is the
      // whole design of the passive. A damage multiplier would let a resistant player stand in a blizzard
      // indefinitely at a slower rate; a wider band means the ice sheet is simply *comfortable* until it is
      // not, which is what "increased tolerance against high and low environment temperatures" says. It also
      // means the skill can never make a survivable place lethal by being retuned.
      val tolerance = resistanceId?.let { id ->
        (world.get(entityId, KnownSkills::class)?.levelOf(id) ?: 0) * config.tolerancePerResistanceLevel
      } ?: 0.0

      // How far outside the comfort band, in degrees. Zero inside it, which is most of the world most of the
      // year - the low-level country is meant to be comfortable, and `REMINDER.md` asks for exactly that.
      val low = config.comfortLowCelsius - tolerance
      val high = config.comfortHighCelsius + tolerance
      val excess = when {
        air < low -> low - air
        air > high -> air - high
        else -> 0.0
      }
      if (excess <= 0.0) return@each

      val cost = (excess * config.staminaPerDegree).roundToInt().coerceAtLeast(1)

      if (stamina.current > 0) {
        stamina.current -= cost
        drained++
      } else {
        val health = world.get(entityId, Health::class) ?: return@each
        health.current -= (cost * config.healthShare).roundToInt().coerceAtLeast(1)
        hurt++
      }
    }
  }

  /** For the counters to be visible without a debugger; a stuck zero is the shipped-dead signal. */
  fun counters(): Pair<Long, Long> = drained to hurt

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
