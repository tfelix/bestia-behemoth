package net.bestia.messages.entity

/**
 * Entity attack message which is issued if an entity used an attack/skill
 * against another entity.
 *
 * @author Thomas Felix
 */
data class SkillUseEntity(
    override val entityId: Long,
    val targetEntityId: Long,
    val attackId: Long
) : EntityMessage