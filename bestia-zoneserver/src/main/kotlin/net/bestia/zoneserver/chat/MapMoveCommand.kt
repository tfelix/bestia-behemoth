package net.bestia.zoneserver.chat

import mu.KotlinLogging
import net.bestia.model.account.Account
import net.bestia.model.account.AccountType
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.entity.component.SetPositionToAbsolute
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.PlayerEntityService
import org.springframework.stereotype.Component
import java.util.regex.Pattern

private val LOG = KotlinLogging.logger { }

/**
 * Moves the player to the given map coordinates if he has GM permissions.
 *
 * @author Thomas Felix
 */
@Component
internal class MapMoveCommand(
    messageApi: MessageApi,
    private val playerBestiaService: PlayerEntityService
) : BaseChatCommand(messageApi) {
  override val helpText: String
    get() = "Usage: /mm <X> <Y> [<Z>]"

  override fun isCommand(text: String): Boolean {
    return text.startsWith("/mm ")
  }

  override fun requiredUserLevel(): AccountType {
    return AccountType.GM
  }

  override fun executeCommand(account: Account, text: String) {
    // Its okay, now execute the command.
    val match = cmdPattern.matcher(text)

    if (!match.find()) {
      LOG.debug("Wrong command usage: {}", text)
      sendSystemMessage(account.id, helpText)
      return
    }

    val x = match.group(1).toLong()
    val y = match.group(2).toLong()
    val z = 0L // FIXME

    if (x < 0 || y < 0) {
      sendSystemMessage(account.id, "Coordinates must be positive.")
      throw IllegalArgumentException("X and Y can not be negative.")
    }

    // TODO Calculate the right Z value if not given. Need to query the map data.

    LOG.info { "Command: /mm triggered by account ${account.id}" }

    playerBestiaService.getActivePlayerEntityId(account.id)?.let { activePlayerBestia ->
      messageApi.send(
          SetPositionToAbsolute(
              entityId = activePlayerBestia,
              position = Vec3(x, y, z)
          )
      )
    }
  }

  companion object {
    private val cmdPattern = Pattern.compile("/mm (\\d+) (\\d+)")
  }
}
