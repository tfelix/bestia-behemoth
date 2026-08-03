package net.bestia.zone.environment.weather

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.socket.OutMessageHandler
import net.bestia.zone.world.stream.ChunkService
import org.springframework.core.annotation.Order
import kotlin.math.abs
import org.springframework.stereotype.Component as SpringComponent

/**
 * Tells each player what the sky is doing, and stops telling them when nothing has changed.
 *
 * ### Cheap by construction
 *
 * `writes` is empty, so `SystemScheduler` may run this fully in parallel with everything else. The work is one
 * O(1) field evaluation per online player per interval, so a thousand players at ten seconds is a hundred
 * evaluations a second - which is why the interval is a real setting rather than a tick budget.
 *
 * ### Hysteresis is not an optimisation
 *
 * Without it a channel sitting on a threshold flips every interval and the client crossfades forever. Sending
 * on a *kind* change alone is not enough either: temperature drifts continuously and a player walking uphill
 * wants to see it move. So there are bands, plus a heartbeat so a client that dropped a message resyncs rather
 * than believing in yesterday's sky until it happens to change.
 *
 * ### The self-cleaning map
 *
 * An account with no anchor this tick has no active entity - it has not picked a master, or it went away - so it
 * is dropped from [lastSent] rather than left there. Same shape as `ChunkStreamSystem.queued`, and it is why
 * there is no subscription service and no disconnect listener here.
 */
@SpringComponent
@Order(46)
class WeatherSystem(
  private val weatherService: WeatherService,
  private val chunkService: ChunkService,
  private val out: OutMessageHandler,
  private val config: WeatherConfig,
  private val skills: SkillRepository
) : System {

  override val schedule: Schedule get() = Schedule.EverySeconds(config.evaluationSeconds)

  override val reads: ComponentClassSet = setOf(Account::class, ActivePlayer::class, KnownSkills::class)

  /**
   * The WEATHER_SENSE skill's id, resolved once.
   *
   * By identifier rather than by the literal 39, because the number in `skills.yml` is content and this is
   * code. Null when the skill is absent from the catalogue, in which case nobody gets a forecast - which is
   * the right behaviour and not a crash.
   */
  private val weatherSenseId: Long? by lazy { skills.findByIdentifier(WEATHER_SENSE)?.id }

  /** Deliberately empty: this changes no component, so it may run beside everything. */
  override val writes: ComponentClassSet = emptySet()

  private val lastSent = HashMap<Long, WeatherSMSG>()
  private val lastSentAt = HashMap<Long, Long>()

  private var evaluations = 0L
  private var messages = 0L
  private var loggedAt = 0L

  override fun update(world: World, deltaTime: Float) {
    if (!config.enabled) return

    val seen = HashSet<Long>()
    val now = java.lang.System.currentTimeMillis()

    world.query(Position::class, Account::class, ActivePlayer::class).each { entityId ->
      val accountId = get<Account>().accountId
      val position = get<Position>()
      seen.add(accountId)

      // The ground under the player rather than the player's own z: temperature is a property of the place,
      // and a player who has jumped is not two degrees colder. Null means off the grid, which is a different
      // bug from being at the wrong height - skip rather than invent a surface.
      val ground = chunkService.surfaceElevationAt(position.x, position.y) ?: return@each

      val at = weatherService.at(position.x, position.y, ground)
      evaluations++

      val hazardVoxel = at.state.hazard?.let { hazard ->
        val voxel = chunkService.config.voxelSize
        (hazard.position.x / voxel).toLong() to (hazard.position.y / voxel).toLong()
      }

      // Only for an entity that has the passive, and scaled by its level: the skill sells "+5min/lv how far
      // ahead upcoming weather changes can be sensed". Absent skill means no forecast field at all rather than
      // an empty one, so a client can tell "you cannot sense weather" from "nothing is coming".
      val lookahead = weatherSenseId?.let { id ->
        val level = world.get(entityId, KnownSkills::class)?.levelOf(id) ?: 0
        if (level > 0) level * FORESIGHT_SECONDS_PER_LEVEL else null
      }
      val forecast = lookahead?.let {
        weatherService.forecast(position.x, position.y, ground, it)
      }

      val message = WeatherSMSG.of(at.regionId, at.state, at.temperature, hazardVoxel, forecast)
      val previous = lastSent[accountId]
      val since = now - (lastSentAt[accountId] ?: 0L)

      if (previous == null || changed(previous, message) || since >= config.heartbeatSeconds * 1_000L) {
        out.sendMessage(accountId, message)
        lastSent[accountId] = message
        lastSentAt[accountId] = now
        messages++
      }
    }

    lastSent.keys.retainAll(seen)
    lastSentAt.keys.retainAll(seen)

    // A counter stuck at zero is the cheapest possible detector for a subsystem that is complete, tested and
    // never reached - which this module has shipped three times.
    if (now - loggedAt >= COUNTER_LOG_MILLIS) {
      loggedAt = now
      LOG.debug { "Weather: $evaluations evaluations, $messages messages sent" }
    }
  }

  /**
   * Whether anything a player could notice has moved.
   *
   * The region token counts as a change on its own: crossing a border into weather that happens to look the
   * same is still the moment a client should stop crossfading from the old region's state.
   */
  private fun changed(previous: WeatherSMSG, current: WeatherSMSG): Boolean =
    previous.kind != current.kind ||
        previous.regionId != current.regionId ||
        abs(previous.cloudCover - current.cloudCover) >= config.cloudBand ||
        abs(previous.intensity - current.intensity) >= config.intensityBand ||
        abs(previous.temperatureCelsius - current.temperatureCelsius) >= config.temperatureBand ||
        abs(previous.windSpeed - current.windSpeed) >= config.windBand ||
        (previous.hazardX != current.hazardX || previous.hazardY != current.hazardY)

  private companion object {
    private val LOG = KotlinLogging.logger { }
    private const val COUNTER_LOG_MILLIS = 300_000L

    private const val WEATHER_SENSE = "WEATHER_SENSE"

    /** Real seconds of foresight each level of WEATHER_SENSE buys. From the skill's own description. */
    private const val FORESIGHT_SECONDS_PER_LEVEL = 300
  }
}
