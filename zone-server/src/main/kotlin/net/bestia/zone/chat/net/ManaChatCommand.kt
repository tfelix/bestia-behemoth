package net.bestia.zone.chat.net

import net.bestia.account.Authority
import net.bestia.zone.chat.ChatCommand
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.world.mana.ManaConcentrationService
import net.bestia.zone.world.mana.ManaGround
import net.bestia.zone.worldevent.gm.GmManaLayer
import org.springframework.stereotype.Component
import java.util.Locale

/** Reads and changes the mana field where the caller stands, to watch it creep and leak. */
@Component
class ManaChatCommand(
  private val connectionInfoService: ConnectionInfoService,
  private val mana: ManaConcentrationService,
  private val ground: ManaGround,
  private val world: WorldView,
  private val out: OutMessageProcessor,
  private val layer: GmManaLayer,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.WORLD_EVENTS

  override fun getHelpText(): String {
    return "/mana - The mana where you stand. /mana set <DENSITY> [RADIUS] - Sets the density. " +
      "/mana add <MOTES> [RADIUS] - Releases motes, or takes them with a negative number. " +
      "RADIUS is in metres, up to $MAX_RADIUS, and defaults to one cell. " +
      "/mana layer on|off - Draws the field, the provinces and the director on your map."
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim()) || LAYER_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    LAYER_REGEX.find(cmdText.trim())?.let { return toggleLayer(playerId, it.groupValues[1] == "on") }

    val match = CMD_REGEX.find(cmdText.trim()) ?: return false
    val (verb, amountText, radiusText) = match.destructured
    val amount = amountText.toDoubleOrNull()
    val radius = radiusText.toDoubleOrNull() ?: 0.0
    if (verb.isNotEmpty() && amount == null) return false
    if (radius > MAX_RADIUS) return false

    val masterEntity = connectionInfoService.getSelectedMasterEntityId(playerId)
    val answer = world.modify(masterEntity) { id ->
      val at = get(id, Position::class)?.toVec3L() ?: return@modify "You are nowhere."
      when (verb) {
        "set" -> mana.setDensity(at, amount!!, radius)
        "add" -> mana.release(at, amount!!, radius)
      }
      describe(at)
    }

    out.sendToPlayer(playerId, ChatSMSG(text = answer ?: "No master in the world.", type = ChatType.COMMAND))

    return true
  }

  private fun toggleLayer(playerId: Long, on: Boolean): Boolean {
    if (on) layer.turnOn(playerId) else layer.turnOff(playerId)
    out.sendToPlayer(playerId, ChatSMSG(text = "Mana layer ${if (on) "on" else "off"}.", type = ChatType.COMMAND))
    return true
  }

  private fun describe(at: Vec3L): String {
    val cell = mana.cellAt(at)

    return String.format(
      Locale.ROOT,
      "Mana density %.3f, start %.3f, potential %.2f, cell (%d, %d).",
      mana.densityOf(cell), ground.startDensity(cell), ground.potentialOf(cell), cell.x, cell.y,
    )
  }

  private companion object {
    const val MAX_RADIUS = 2000.0

    val LAYER_REGEX = Regex("""^/mana\s+layer\s+(on|off)$""")

    val CMD_REGEX = Regex("""^/mana(?:\s+(set|add)\s+(-?\d+(?:\.\d+)?)(?:\s+(\d+(?:\.\d+)?))?)?$""")
  }
}
