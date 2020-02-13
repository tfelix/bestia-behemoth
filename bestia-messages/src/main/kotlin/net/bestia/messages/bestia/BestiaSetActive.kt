package net.bestia.messages.bestia

import net.bestia.messages.AccountMessage

import net.bestia.messages.entity.EntityMessage

/**
 * Client sends this message if it wants to switch to another active bestia.
 * This bestia from now on is responsible for gathering all visual information.
 * And the client will get updated about these data.
 *
 * @author Thomas Felix
 */
data class BestiaSetActive(
    override val accountId: Long,
    override val entityId: Long,
    val playerBestiaId: Long
) : EntityMessage, AccountMessage
