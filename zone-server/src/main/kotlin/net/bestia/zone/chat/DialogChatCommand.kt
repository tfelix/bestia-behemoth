package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.dialog.DialogDefinition
import net.bestia.zone.dialog.DialogDefinitionRegistry
import net.bestia.zone.dialog.DialogService
import org.springframework.stereotype.Component

/**
 * Pushes any catalogued dialog to the sender's own client, so dialog text, placeholders and BBCode
 * can be checked without needing whatever gameplay trigger will eventually send it. Every declared
 * placeholder is filled with an obvious dummy value (`<name>`), which keeps the substitution path
 * exercised and makes a placeholder the client failed to replace visible at a glance.
 */
@Component
class DialogChatCommand(
  private val dialogDefinitionRegistry: DialogDefinitionRegistry,
  private val dialogService: DialogService
) : ChatCommand() {

  companion object {
    private val LOG = KotlinLogging.logger { }
    private val CMD_REGEX = Regex("""^/dialog\s+(\S+)$""")
  }

  override fun getHelpText(): String {
    return "/dialog <DIALOG_ID | DIALOG_IDENTIFIER> - Shows that dialog on your own client, with dummy placeholder values."
  }

  override val requiredAuthority: Authority = Authority.DIALOG

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val match = CMD_REGEX.find(cmdText.trim()) ?: return false
    val dialogArg = match.groupValues[1]

    val definition = dialogArg.toIntOrNull()
      ?.let { dialogDefinitionRegistry.findById(it) }
      ?: dialogDefinitionRegistry.findByIdentifier(dialogArg)

    if (definition == null) {
      LOG.warn { "Dialog command failed: dialog '$dialogArg' is not in dialogs.yml" }
      return false
    }

    dialogService.send(playerId, definition, dummyArgs(definition))

    LOG.info { "Sent dialog ${definition.identifier} (id=${definition.id}) to player $playerId via chat command" }

    return true
  }

  private fun dummyArgs(definition: DialogDefinition): Map<String, DialogArg> =
    definition.args.associateWith { DialogArg.Text("<$it>") }
}
