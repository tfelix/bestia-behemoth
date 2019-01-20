package net.bestia.zoneserver.chat

import mu.KotlinLogging
import net.bestia.model.account.AccountRepository
import net.bestia.model.dao.findOneOrThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*

private val LOG = KotlinLogging.logger { }

/**
 * The service always tries to find all implementations of [ChatCommand]
 * and loads them upon creation. All the incoming chat commands are tested for
 * this input.
 *
 */
@Service
class ChatCommandService @Autowired
constructor(chatCommands: List<ChatCommand>, accDao: AccountRepository) {

  private val chatCommands = ArrayList<ChatCommand>()
  private val accountDao: AccountRepository

  init {

    this.chatCommands.addAll(Objects.requireNonNull(chatCommands))
    this.accountDao = Objects.requireNonNull(accDao)
  }

  /**
   * Checks if the given text string contains a chat command.
   *
   * @param text
   * The chat text to check against a command.
   * @return TRUE if it contains a executable command. FALSE otherwise.
   */
  fun isChatCommand(text: String): Boolean {
    return text.startsWith(CMD_PREFIX)
  }

  /**
   * Executes the chat command in the context of the given account id.
   *
   * @param accId
   * The account who is executing this chat command.
   * @param text
   * The text containing the command.
   */
  fun executeChatCommand(accId: Long, text: String) {
    LOG.debug("Account {} used chat command. Message: {}", accId, text)

    // First small check if we have potentially a command or if the can stop
    // right away.
    if (!isChatCommand(text)) {
      return
    }

    val acc = accountDao.findOneOrThrow(accId)

    chatCommands.first { it.isCommand(text) && acc.userLevel >= it.requiredUserLevel() }
            .executeCommand(acc, text)
  }

  companion object {
    const val CMD_PREFIX = "/"
  }
}
