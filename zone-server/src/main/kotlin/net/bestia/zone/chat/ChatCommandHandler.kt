package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
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
          outMessageProcessor.sendToPlayer(playerId, OperationErrorSMSG(OpError.CHAT_COMMAND_FAILED))
          return
        }
      }

      // Nothing ran, and the player is owed the reason. A command they typed correctly but may not use is a
      // different problem from one that does not exist, and answering both with "unknown" reads as the
      // feature being missing.
      val deniedByAuthority = commands.any { it.isMatch(cmdText) && !it.isAvailable(playerAuthorities) }

      if (deniedByAuthority) {
        LOG.debug { "Player $playerId lacks the authority for '$cmdText'" }
        outMessageProcessor.sendToPlayer(playerId, OperationErrorSMSG(OpError.CHAT_COMMAND_NO_PERMISSION))
      } else {
        LOG.debug { "No chat command found for player $playerId with '$cmdText'" }
        outMessageProcessor.sendToPlayer(playerId, OperationErrorSMSG(OpError.CHAT_COMMAND_UNKNOWN))
      }
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