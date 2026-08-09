package net.bestia.zone.environment.weather

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.socket.OutMessageHandler
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Evaluates the weather where one player is standing and sends it, when anything they could notice has moved.
 *
 * ### Why this is not simply part of [WeatherSystem]
 *
 * Two callers need it, and they have to share one record of what each account was last told. [WeatherSystem]
 * sweeps every online player on its own interval; `SelectMasterHandler` fires once, the instant a master is
 * spawned, so a client has a sky before its first frame rather than up to `weather.evaluation-seconds` later.
 * Give each its own map and selecting a master sends the same message twice - which is exactly the
 * client-visible flicker the hysteresis below exists to prevent.
 *
 * ### Hysteresis is not an optimisation
 *
 * Without it a channel sitting on a threshold flips every interval and the client crossfades forever. Sending
 * on a *kind* change alone is not enough either: temperature drifts continuously and a player walking uphill
 * wants to see it move. So there are bands, plus a heartbeat so a client that dropped a message resyncs rather
 * than believing in yesterday's sky until it happens to change.
 *
 * ### Nothing here touches the ECS, deliberately
 *
 * The two callers are on different threads - [WeatherSystem] on `zone-tick`, the handler on a Netty worker -
 * so the maps are concurrent. Both callers read the position and the skill level themselves and hand over
 * plain values, which keeps this class out of the question of who may take the world lock and when.
 */
@Service
class WeatherPublisher(
  private val weatherService: WeatherService,
  private val chunkService: ChunkService,
  private val out: OutMessageHandler,
  private val config: WeatherConfig,
  private val skills: SkillRepository,
) {

  /**
   * The WEATHER_SENSE skill's id, resolved once.
   *
   * By identifier rather than by the literal 39, because the number in `skills.yml` is content and this is
   * code. Null when the skill is absent from the catalogue, in which case nobody gets a forecast - which is
   * the right behaviour and not a crash.
   *
   * Visible because both callers resolve a player's level against it before calling [publish]: that level
   * lives in a `KnownSkills` component, and reading components is the caller's business - see the class note.
   */
  val weatherSenseSkillId: Long? by lazy { skills.findByIdentifier(WEATHER_SENSE)?.id }

  private val lastSent = ConcurrentHashMap<Long, WeatherSMSG>()
  private val lastSentAt = ConcurrentHashMap<Long, Long>()

  private var evaluations = 0L
  private var messages = 0L
  private var loggedAt = 0L

  /**
   * Evaluates the weather at a voxel position and sends it to [accountId], unless nothing has moved since the
   * last message and the heartbeat is not yet due.
   *
   * @param weatherSenseLevel the player's WEATHER_SENSE level; `0` for no forecast at all
   * @return whether a message actually went out
   */
  fun publish(accountId: Long, voxelX: Long, voxelY: Long, weatherSenseLevel: Int): Boolean {
    if (!config.enabled) return false

    // The ground under the player rather than the player's own z: temperature is a property of the place, and
    // a player who has jumped is not two degrees colder. Null means off the grid, which is a different bug
    // from being at the wrong height - skip rather than invent a surface.
    val ground = chunkService.surfaceElevationAt(voxelX, voxelY) ?: return false

    val at = weatherService.at(voxelX, voxelY, ground)
    evaluations++

    val hazardVoxel = at.state.hazard?.let { hazard ->
      val voxel = chunkService.config.voxelSize
      (hazard.position.x / voxel).toLong() to (hazard.position.y / voxel).toLong()
    }

    // Only for a player who has the passive, and scaled by its level: the skill sells "+5min/lv how far ahead
    // upcoming weather changes can be sensed". No skill means no forecast field at all rather than an empty
    // one, so a client can tell "you cannot sense weather" from "nothing is coming".
    val forecast = if (weatherSenseLevel <= 0) null else {
      weatherService.forecast(voxelX, voxelY, ground, weatherSenseLevel * FORESIGHT_SECONDS_PER_LEVEL)
    }

    val message = WeatherSMSG.of(at.regionId, at.state, at.temperature, hazardVoxel, forecast)

    val now = System.currentTimeMillis()
    val previous = lastSent[accountId]
    val since = now - (lastSentAt[accountId] ?: 0L)

    logCounters(now)

    val due = previous == null ||
        changed(previous, message) ||
        since >= config.heartbeatSeconds * 1_000L
    if (!due) return false

    out.sendMessage(accountId, message)
    lastSent[accountId] = message
    lastSentAt[accountId] = now
    messages++

    return true
  }

  /**
   * Forgets every account not in [accounts].
   *
   * An account with no anchor on a sweep has no active entity - it has not picked a master, or it went away -
   * so it is dropped rather than left here. Same shape as `ChunkStreamSystem.queued`, and it is why this
   * package has no subscription service and no disconnect listener.
   *
   * One benign race, worth naming rather than locking against: a master selected *after* [WeatherSystem]
   * finished its query but *before* it calls this will have its fresh entry dropped, and the next sweep sends
   * the same message again. That costs one redundant message, at most once per login, and only inside a
   * window of a single tick.
   */
  fun retainOnly(accounts: Set<Long>) {
    lastSent.keys.retainAll(accounts)
    lastSentAt.keys.retainAll(accounts)
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

  /**
   * A counter stuck at zero is the cheapest possible detector for a subsystem that is complete, tested and
   * never reached - which this module has shipped three times.
   */
  private fun logCounters(now: Long) {
    if (now - loggedAt < COUNTER_LOG_MILLIS) return

    loggedAt = now
    LOG.debug { "Weather: $evaluations evaluations, $messages messages sent" }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
    private const val COUNTER_LOG_MILLIS = 300_000L

    private const val WEATHER_SENSE = "WEATHER_SENSE"

    /** Real seconds of foresight each level of WEATHER_SENSE buys. From the skill's own description. */
    private const val FORESIGHT_SECONDS_PER_LEVEL = 300
  }
}
