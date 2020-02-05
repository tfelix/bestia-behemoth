package net.bestia.messages.ui

import net.bestia.messages.AccountMessage

/**
 * Asks the server to replay with a list of shortcuts for the current entity and
 * the account.
 *
 * @author Thomas Felix
 */
data class ClientVarRequest(
    override val accountId: Long,
    val key: String,
    var uuid: String
) : AccountMessage
