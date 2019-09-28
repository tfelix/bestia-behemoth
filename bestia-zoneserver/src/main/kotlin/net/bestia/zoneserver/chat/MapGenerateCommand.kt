package net.bestia.zoneserver.chat

import akka.actor.ActorRef
import akka.actor.ActorSystem
import mu.KotlinLogging
import net.bestia.model.account.Account
import net.bestia.model.account.AccountType
import net.bestia.model.map.MapParameter
import net.bestia.zoneserver.AkkaCluster
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.actor.map.MapGeneratorMasterActor
import org.springframework.stereotype.Component
import java.util.regex.Pattern

private val LOG = KotlinLogging.logger { }

/**
 * Generates a new map upon command. This will basically send a message to start
 * the map generation to the appropriate actor.
 */
@Component
internal class MapGenerateCommand(
    messageApi: MessageApi,
    private val system: ActorSystem
) : BaseChatCommand(messageApi) {

  override val helpText: String
    get() = "Usage: /genMap <MAPNAME> <USERCOUNT>"

  override fun isCommand(text: String): Boolean {
    return text.matches(CMD_START_REGEX.toRegex())
  }

  override fun requiredUserLevel(): AccountType {
    return AccountType.ADMIN
  }

  override fun executeCommand(account: Account, text: String) {
    // Extract name of the new map.
    val match = CMD_PATTERN.matcher(text)

    if (!match.find()) {
      printError(account.id)
      return
    }

    val mapName = match.group(1)
    val userCount: Int

    try {
      userCount = Integer.parseInt(match.group(2))
    } catch (e: Exception) {
      printError(account.id)
      return
    }

    if (mapName == null) {
      printError(account.id)
      return
    }

    LOG.info("Map generation triggerd by {}. Put cluster into maintenance mode and generate new world.",
        account.id)

    // Create the base params.
    val baseParams = MapParameter.fromAverageUserCount(userCount, mapName)

    LOG.info("New map parameter: {}", baseParams)

    // Perform the map generation.
    val nodeName = AkkaCluster.getNodeName(MapGeneratorMasterActor.NAME)
    val selection = system.actorSelection(nodeName)
    selection.tell(baseParams, ActorRef.noSender())
  }

  private fun printError(accId: Long) {
    sendSystemMessage(accId, "No mapname given. Usage: /genMap <MAPNAME> <USERCOUNT>")
  }

  companion object {
    private const val CMD_START_REGEX = "^/genmap .*"
    private val CMD_PATTERN = Pattern.compile("/genmap (.*?) (\\d+)")
  }
}
