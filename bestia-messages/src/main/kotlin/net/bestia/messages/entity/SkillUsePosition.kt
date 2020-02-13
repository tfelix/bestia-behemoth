package net.bestia.messages.entity

/**
 * Entity attack message which is issued if an entity used an attack/skill
 * against a coordinate on the ground.
 *
 * @author Thomas Felix
 */
data class SkillUsePosition(
    override val entityId: Long,
    val x: Long,
    val y: Long,
    val z: Long,
    val attackId: Long
) : EntityMessage
