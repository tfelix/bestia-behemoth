package net.bestia.messages.ui

import net.bestia.messages.AccountMessage

/**
 * This message will trigger the client to open a dialog NPC box.
 *
 * @author Thomas Felix
 */
data class DialogMessage(
    override val accountId: Long,
    private val nodes: List<DialogNode>
) : AccountMessage
