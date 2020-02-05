package net.bestia.messages.entity

import net.bestia.messages.EntityMessage

/**
 * This message can be used to communicate a received damage to an entity. If
 * more then one damage is returned it will be a multi damage display. Otherwise
 * it is a simple hit.
 *
 * @author Thomas Felix
 */
data class EntityDamage(
    override val entityId: Long,
    val amount: Int,
    val type: DamageType
) : EntityMessage
