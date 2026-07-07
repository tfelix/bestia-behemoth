package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.message.processor.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class ChatCommandHandler(
  private val commands: List<ChatCommand>,
  private val connectionInfoService: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor
) {

  companion object {
    private val LOG = KotlinLogging.logger { }
  }

  fun handleChatCommand(playerId: Long, cmdText: String) {
    if (isHelpCommand(cmdText)) {
      printHelpText(playerId)
    } else {
      val playerAuthorities = connectionInfoService.getAuthorities(playerId)

      commands.forEach { cmd ->
        try {
          if (cmd.tryExecute(playerId, cmdText, playerAuthorities)) {
            LOG.trace { "Chat command executed: ${cmd.javaClass.simpleName} for player $playerId with '$cmdText'" }
            return
          }
        } catch (e: Exception) {
          LOG.error(e) { "Chat command ${cmd.javaClass.simpleName} failed for player $playerId with '$cmdText'" }
          val errorMsg = ChatSMSG(text = "error.command_failed", type = ChatCMSG.Type.ERROR)
          outMessageProcessor.sendToPlayer(playerId, errorMsg)
          return
        }
      }

      LOG.debug { "No chat command found for player $playerId with '$cmdText'" }
      outMessageProcessor.sendToPlayer(playerId, ChatSMSG.ERROR_NOT_SUPPORTED)
    }
  }

  private fun printHelpText(playerId: Long) {
    val playerAuthorities = connectionInfoService.getAuthorities(playerId)

    val helpText = commands
      .filter { it.isAvailable(playerAuthorities) }
      .joinToString("\n") { it.getHelpText() }

    val chatSMSG = ChatSMSG(
      text = helpText,
      type = ChatCMSG.Type.COMMAND,
      senderUsername = null,
      senderEntityId = null
    )

    outMessageProcessor.sendToPlayer(playerId, chatSMSG)
  }

  private fun isHelpCommand(cmdText: String): Boolean {
    return cmdText.startsWith("/help")
  }
}