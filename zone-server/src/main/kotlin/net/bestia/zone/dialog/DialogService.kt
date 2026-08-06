package net.bestia.zone.dialog

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * The entry point for making a client show a dialog - what NPCs, scripts and message handlers call.
 * Resolves the [DialogDefinition] from the catalog, checks the supplied placeholders against the
 * ones it declares, and sends a [DialogSMSG] to that one account.
 *
 * Deliberately the *only* place that builds a [DialogSMSG], so callers never touch
 * [OutMessageProcessor] and every dialog goes through the same argument validation:
 *
 * ```
 * dialogService.send(accountId, DialogId.MASTER_INTRO, mapOf("masterName" to DialogArg.Text(name)))
 * ```
 *
 * Sending is immediate and fire-and-forget. Nothing waits for the player to close the dialog, and a
 * dialog that arrives before the client's UI exists is buffered client-side, not here.
 */
@Service
class DialogService(
  private val dialogDefinitionRegistry: DialogDefinitionRegistry,
  private val outMessageProcessor: OutMessageProcessor,
) {

  fun send(
    accountId: AccountId,
    dialog: DialogId,
    args: Map<String, DialogArg> = emptyMap(),
    sourceEntityId: EntityId? = null,
  ) {
    dispatch(dialogDefinitionRegistry.getOrThrow(dialog), accountId, args, sourceEntityId)
  }

  /**
   * Sends a dialog resolved by raw catalog id. Only for callers that genuinely don't know the
   * dialog at compile time (the `/dialog` dev chat command) - everything else should use the
   * [DialogId] overload so the reference is checked by the compiler.
   */
  fun send(
    accountId: AccountId,
    definition: DialogDefinition,
    args: Map<String, DialogArg> = emptyMap(),
    sourceEntityId: EntityId? = null,
  ) {
    dispatch(definition, accountId, args, sourceEntityId)
  }

  private fun dispatch(
    definition: DialogDefinition,
    accountId: AccountId,
    args: Map<String, DialogArg>,
    sourceEntityId: EntityId?,
  ) {
    validateArgs(definition, args)

    outMessageProcessor.sendToPlayer(
      accountId,
      DialogSMSG(
        dialogId = definition.id,
        type = definition.type,
        args = args,
        sourceEntityId = sourceEntityId
      )
    )

    LOG.debug { "Sent dialog ${definition.identifier} (id=${definition.id}) to account $accountId" }
  }

  /**
   * A mismatch here means the translated text either renders a literal `{placeholder}` or silently
   * drops a value the author meant to show, and both only surface as a visual bug in one locale -
   * so this throws rather than logs.
   */
  private fun validateArgs(definition: DialogDefinition, args: Map<String, DialogArg>) {
    val declared = definition.args.toSet()
    val supplied = args.keys

    if (declared == supplied) {
      return
    }

    val missing = declared - supplied
    val unexpected = supplied - declared

    throw IllegalArgumentException(
      "Dialog ${definition.identifier} (id=${definition.id}) declares args $declared but was sent $supplied" +
        (if (missing.isNotEmpty()) "; missing: $missing" else "") +
        (if (unexpected.isNotEmpty()) "; unexpected: $unexpected" else "")
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
