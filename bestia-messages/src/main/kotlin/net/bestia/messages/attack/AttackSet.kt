package net.bestia.messages.attack

import net.bestia.messages.AccountMessage

/**
 * Sets the attacks of the currently active bestia.
 *
 * @author Thomas Felix
 */
data class AttackSet(
    override val accountId: Long,
    val attackSlotIds: List<Int>
) : AccountMessage
