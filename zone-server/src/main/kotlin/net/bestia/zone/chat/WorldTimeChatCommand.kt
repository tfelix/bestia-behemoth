package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.time.BestiaDateTime
import net.bestia.zone.environment.time.WorldTimeSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

/**
 * `/date [<YEAR>-<MONTH>-<DAY>] <HH:MM>` - moves the world calendar. In memory only.
 *
 * A Bestia day takes eight real hours, a season ten real days and a year forty. Everything downstream of the
 * clock is therefore untestable by waiting: night, the seasonal swing in the temperature model, the weather
 * field's `t`, and the AI's activity cycle each need the calendar to be somewhere it will not reach during a
 * session. This puts it there.
 *
 * ### Nothing is written back, and that is the design rather than a shortcut
 *
 * [BestiaClock] applies an in-process offset and leaves the world's `createdAt` - the row the calendar is
 * anchored to - untouched. So a restart is the reset, no persistence layer learns about a debugging tool, and
 * a GM cannot permanently push a live world's calendar somewhere by typing a wrong number. `/date reset`
 * exists for the same reason but without the restart.
 *
 * ### Why it broadcasts
 *
 * There is one clock. Clients run their own copy of it forward off the anchor they were given at login
 * ([WorldTimeSMSG] documents why it is an anchor and not a tick), so a jump that was not pushed would leave
 * the server in the dark and every client still in the afternoon - and the *other* players' clients would
 * never find out at all. The push goes to every connected account, including ones still on master select,
 * because their clock is already running.
 *
 * ### It does not go through the tick
 *
 * Unlike [MapMoveChatCommand] and [CarveChatCommand], which queue their work onto `zone-tick` because they
 * touch the chunk store. Nothing here does: the clock's offset is a single volatile field, and everything
 * that reads it - the weather field, the temperature model, the activity cycle - is a pure function of the
 * time it is handed, with no cached "current weather" to invalidate. The next reader sees the new time.
 */
@Component
class WorldTimeChatCommand(
  private val clock: BestiaClock,
  private val out: OutMessageProcessor
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.WORLD_TIME

  override fun getHelpText() =
    "/date - Shows the world date. /date <HH:MM> jumps to that time today; /date <YEAR>-<MONTH>-<DAY> " +
        "<HH:MM> jumps to a full date; /date reset returns to real time. Months are " +
        "1..${BestiaDateTime.MONTHS_PER_YEAR} (one per season) and days 1..${BestiaDateTime.DAYS_PER_MONTH}. " +
        "Dusk falls from ${BestiaDateTime.DUSK_START_HOUR}:00, it is fully dark from " +
        "${BestiaDateTime.NIGHT_START_HOUR}:00 to 0${BestiaDateTime.NIGHT_END_HOUR}:00, and dawn is done by " +
        "0${BestiaDateTime.DAWN_END_HOUR}:00. The change is in memory only and everyone online is told about it."

  override fun isMatch(cmdText: String) = CMD_REGEX.matches(cmdText.trim())

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val match = CMD_REGEX.find(cmdText.trim()) ?: return false

    val isReset = match.groupValues[RESET].isNotEmpty()
    val hourText = match.groupValues[HOUR]

    // Bare `/date`, which reports without changing anything. Worth having as its own case: the HUD clock is
    // the client's own extrapolation, so this is the only way to read what the *server* thinks the time is.
    if (!isReset && hourText.isEmpty()) {
      reply(playerId, describe(clock.now()))

      return true
    }

    if (isReset) {
      val now = clock.resetToRealTime()
      announce(now)

      LOG.info { "Player $playerId reset the world clock to real time: ${describe(now)}" }
      reply(playerId, "World clock back on real time. ${describe(now)}")

      return true
    }

    val current = clock.now()
    val target = try {
      BestiaDateTime(
        year = match.groupValues[YEAR].takeIf { it.isNotEmpty() }?.toLong() ?: current.year,
        month = match.groupValues[MONTH].takeIf { it.isNotEmpty() }?.toInt() ?: current.month,
        day = match.groupValues[DAY].takeIf { it.isNotEmpty() }?.toInt() ?: current.day,
        hour = hourText.toInt(),
        minute = match.groupValues[MINUTE].toInt(),
        second = 0
      )
    } catch (e: IllegalArgumentException) {
      // BestiaDateTime validates its own ranges, so the message already names the field and the bound. Handed
      // straight back rather than turned into "command failed", which would leave the caller guessing which
      // of four numbers was the wrong one.
      reply(playerId, "Not a date in this world: ${e.message}")

      return true
    }

    val now = clock.jumpTo(target)
    val recipients = announce(now)

    LOG.info { "Player $playerId set the world clock to ${describe(now)}; told $recipients client(s)" }
    reply(playerId, "World clock set. ${describe(now)}")

    return true
  }

  /** Re-anchors every connected client's calendar. @return how many were told */
  private fun announce(now: BestiaDateTime): Int =
    out.sendToAllConnected(WorldTimeSMSG.of(now, clock.speedFactor))

  private fun reply(playerId: Long, text: String) {
    out.sendToPlayer(playerId, ChatSMSG(text = text, type = ChatCMSG.Type.COMMAND))
  }

  private fun describe(now: BestiaDateTime): String {
    // The four-way name rather than day/night, because the twilight hours are the ones worth typing this to
    // reach and "day" for 21:00 would read as the command not having worked.
    val phase = when {
      now.isNight -> "night"
      now.daylight >= 1.0 -> "day"
      now.hour < BestiaDateTime.DAWN_END_HOUR -> "dawn"
      else -> "dusk"
    }

    val drift = if (clock.isShifted) " (shifted; /date reset undoes it)" else ""

    return "Year %d, %s, day %d, %02d:%02d - %s%s".format(
      now.year, now.season.name.lowercase(), now.day, now.hour, now.minute, phase, drift
    )
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /**
     * `/date`, `/date reset`, `/date HH:MM`, `/date Y-M-D HH:MM`.
     *
     * The date part is optional and the time is not, rather than the other way round, because the reason to
     * type this is almost always to see a particular hour - a season is thirty days long, so the day the jump
     * lands on is rarely the point.
     */
    private val CMD_REGEX =
      Regex("""^/date(?:\s+(reset)|(?:\s+(\d+)-(\d+)-(\d+))?\s+(\d{1,2}):(\d{2}))?$""")

    private const val RESET = 1
    private const val YEAR = 2
    private const val MONTH = 3
    private const val DAY = 4
    private const val HOUR = 5
    private const val MINUTE = 6
  }
}
