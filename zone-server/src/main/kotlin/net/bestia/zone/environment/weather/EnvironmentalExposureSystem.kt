package net.bestia.zone.environment.weather

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.world.stream.ChunkService
import org.springframework.core.annotation.Order
import kotlin.math.abs
import kotlin.math.roundToInt
import org.springframework.stereotype.Component as SpringComponent

/**
 * Cold and heat cost stamina, and then health.
 *
 * ### This is the system that makes the weather model impossible to ship dead
 *
 * `worldgen` shipped three subsystems in a row that were complete, tested, and never *reached*: sea lanes
 * that produced none on forty worlds, four built sites blocked by an integer overflow, and four seasonal
 * climate layers that were summed and discarded for a year. Every one of them looked finished.
 *
 * Weather without a consumer is in exactly that position: it would keep evaluating, keep sending messages, and
 * nothing in the game would be different if it silently stopped. With this, a player who walks into the
 * mountains in winter starts losing stamina, and if the weather ever stops being computed **they stop losing
 * it** - which is loud, immediate and reported by players rather than discovered in a sweep.
 *
 * ### Stamina first, health second
 *
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
  private val resistanceId: Long? by lazy { skills.findByIdentifier(WEATHER_RESISTANCE)?.id }

  override val schedule: Schedule get() = Schedule.EverySeconds(config.intervalSeconds)

  override val writes: ComponentClassSet = setOf(Stamina::class, Health::class)

  private var drained = 0L
  private var hurt = 0L

  override fun update(world: World, deltaTime: Float) {
    if (!config.enabled) return

    world.query(Position::class, Stamina::class).each { entityId ->
      val position = get<Position>()
      val stamina = get<Stamina>()

      // Off the grid is a different bug from being at the wrong height, and inventing a surface here would
      // hide it. Skip.
      val ground = chunkService.surfaceElevationAt(position.x, position.y) ?: return@each

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
    private const val WEATHER_RESISTANCE = "WEATHER_RESISTANCE"
  }
}
