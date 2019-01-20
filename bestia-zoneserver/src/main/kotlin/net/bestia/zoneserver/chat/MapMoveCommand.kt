package net.bestia.zoneserver.chat

import mu.KotlinLogging
import net.bestia.messages.map.MapMoveMessage
import net.bestia.messages.entity.ComponentClassEnvelope
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.model.account.Account
import net.bestia.model.account.AccountType
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.PlayerEntityService
import net.bestia.zoneserver.entity.component.PositionComponent
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
    get() = "Usage: /mm <X> <Y>"

  override fun isCommand(text: String): Boolean {
    return text.startsWith("/mm ")
  }

  override fun requiredUserLevel(): AccountType {
    return AccountType.GM
  }

  override fun executeCommand(account: Account, text: String) {
    LOG.info { "Command: /mm triggered by account ${account.id}." }

    // Its okay, now execute the command.
    val match = cmdPattern.matcher(text)

    if (!match.find()) {
      LOG.debug("Wrong command usage: {}", text)
      sendSystemMessage(account.id, helpText)
      return
    }

    val x = java.lang.Long.parseLong(match.group(1))
    val y = java.lang.Long.parseLong(match.group(2))

    if (x < 0 || y < 0) {
      sendSystemMessage(account.id, "Illegal coordinates. Must be positive.")
      throw IllegalArgumentException("X and Y can not be negative.")
    }

    val mapMoveMessage = MapMoveMessage(
        account.id,
        Point(x, y)
    )

    playerBestiaService.getActivePlayerEntityId(account.id)?.let {
      messageApi.send(
          EntityEnvelope(
              it,
              ComponentClassEnvelope(PositionComponent::class.java, mapMoveMessage)
          )
      )
    }
  }

  companion object {
    private val cmdPattern = Pattern.compile("/mm (\\d+) (\\d+)")
  }
}
