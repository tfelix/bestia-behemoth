package net.bestia.zone.chat.net

import net.bestia.account.Authority
import net.bestia.zone.chat.ChatCommand
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.doomsday.DoomEnding
import net.bestia.zone.doomsday.DoomKind
import net.bestia.zone.doomsday.DoomsdayDirector
import net.bestia.zone.doomsday.DoomsdayStatus
import net.bestia.zone.doomsday.ForeshadowKind
import net.bestia.zone.doomsday.WorldEnd
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Locale

/** Reads and turns the doom clock, so the doomsday can be seen and tested without waiting months. */
@Component
class DoomChatCommand(
  private val director: DoomsdayDirector,
  private val worldEnd: WorldEnd,
  private val out: OutMessageProcessor,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.WORLD_EVENTS

  override fun getHelpText(): String {
    return "/doom - The doomsday at a glance. /doom set <0..1> - Sets the clock. /doom push <delta> - Moves it. " +
      "/doom pace <x|auto> - Fixes its pace. /doom kind <KIND> - Another threat. /doom foreshadow <KIND> - Another " +
      "Foreshadowing. /doom end <lost|saved> [minutes] - Ends the world. /doom end cancel - Stops the end, a Last " +
      "Stand counts as won. /doom sequence <lost|saved> - Plays the final sequence for you alone. Built threats: " + director.builtKinds().joinToString(", ").ifEmpty { "none" }
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val match = CMD_REGEX.find(cmdText.trim()) ?: return false
    val (verb, argument, extra) = match.destructured
    if (extra.isNotEmpty() && verb != "end") {
      return false
    }

    val answer = when (verb) {
      "" -> describe(director.status())
      "set" -> argument.toDoubleOrNull()?.let { "Doom clock set, now ${director.set(it)}." } ?: return false
      "push" -> argument.toDoubleOrNull()?.let { "Doom clock pushed, now ${director.push(it)}." } ?: return false
      "pace" -> pace(argument) ?: return false
      "kind" -> kind(argument) ?: return false
      "foreshadow" -> foreshadow(argument) ?: return false
      "end" -> end(argument, extra) ?: return false
      "sequence" -> sequence(playerId, argument) ?: return false
      else -> return false
    }

    out.sendToPlayer(playerId, ChatSMSG(text = answer, type = ChatType.COMMAND))
    return true
  }

  private fun pace(argument: String): String? {
    if (argument == "auto") {
      director.overridePace(null)
      return "The threat sets the pace again."
    }
    val pace = argument.toDoubleOrNull() ?: return null
    director.overridePace(pace)
    return "Pace fixed at ${format(pace)}."
  }

  private fun kind(argument: String): String? {
    val kind = DoomKind.entries.firstOrNull { it.name.equals(argument, ignoreCase = true) } ?: return null
    return if (director.repick(kind)) "The world is now fated to $kind." else "$kind is not built or does not fit."
  }

  private fun foreshadow(argument: String): String? {
    val kind = ForeshadowKind.entries.firstOrNull { it.name.equals(argument, ignoreCase = true) } ?: return null
    return if (director.repickForeshadow(kind)) "The Foreshadowing is now $kind." else "$kind is not built."
  }

  private fun end(argument: String, minutesText: String): String? {
    if (argument == "cancel") {
      return worldEnd.beatBack()?.let { "The world's end ($it) is stopped." } ?: "The world's end is not decided."
    }
    val ending = DoomEnding.entries.firstOrNull { it.name.equals(argument, ignoreCase = true) } ?: return null
    val minutes = if (minutesText.isEmpty()) DEFAULT_END_MINUTES else minutesText.toLongOrNull() ?: return null
    val opened = worldEnd.open(ending, Duration.ofMinutes(minutes.coerceAtLeast(1)))
    return "The world ends ($ending) at ${opened.endsAt}."
  }

  private fun sequence(playerId: Long, argument: String): String? {
    val ending = DoomEnding.entries.firstOrNull { it.name.equals(argument, ignoreCase = true) } ?: return null
    out.sendToPlayer(playerId, worldEnd.preview(ending))
    return "Playing the $ending sequence for you. Nothing ends."
  }

  private fun describe(status: DoomsdayStatus): String {
    val origin = status.originX?.let { "(${it.toInt()}, ${status.originY?.toInt()})" } ?: "-"
    val pace = format(status.pace) + if (status.paceOverridden) " fixed" else ""

    return "${status.kind ?: "No threat"} at $origin, ${status.stage} ${format(status.progress)}. " +
      "Pace $pace (pressure ${format(status.pressure)}, containment ${format(status.containment)}). " +
      "Day ${status.ageDays.toInt()} of ${status.lifeSpanDays.toInt()}, onset day ${status.onsetDay.toInt()}, " +
      "T ${format(status.tPercent)}, end about day ${status.projectedEndDay.toInt()}. " +
      "Foreshadowing ${status.foreshadowKind ?: "none"} on day ${status.foreshadowDay.toInt()}." +
      (status.ending?.let { " Fate decided: $it." } ?: "")
  }

  private fun format(value: Double): String {
    return String.format(Locale.ROOT, "%.2f", value)
  }

  private companion object {
    val CMD_REGEX = Regex("""^/doom(?:\s+(set|push|pace|kind|foreshadow|end|sequence)\s+(\S+)(?:\s+(\S+))?)?$""")

    /** Long enough to watch the countdown and the final sequence. */
    const val DEFAULT_END_MINUTES = 5L
  }
}
