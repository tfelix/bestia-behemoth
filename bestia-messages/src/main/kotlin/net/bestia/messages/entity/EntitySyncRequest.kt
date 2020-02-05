package net.bestia.messages.entity

import net.bestia.messages.AccountMessage

/**
 * Requests the server to send a full list with all visible entities to the
 * client. This message is issued by the engine if a reload has occured or the
 * engine is unsure to have synced to all entities.
 *
 * @author Thomas Felix
 */
data class EntitySyncRequest(
    override val accountId: Long
) : AccountMessage
