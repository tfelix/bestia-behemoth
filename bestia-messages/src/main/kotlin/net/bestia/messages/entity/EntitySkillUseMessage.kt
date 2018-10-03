package net.bestia.messages.entity

import java.io.Serializable

import net.bestia.model.geometry.Point

/**
 * Entity attack message which is issued if an entity used an attack/skill
 * against another entity or against a coordinate on the ground.
 *
 * @author Thomas Felix
 */
data class EntitySkillUseMessage(
    val targetPostion: Point?,
    val targetEntityId: Long?,
    val sourceEntityId: Long,
    val attackId: Int
) : Serializable {

  constructor(sourceEntityId: Long, attackId: Int, targetEntityId: Long): this(
      targetPostion = null,
      targetEntityId = targetEntityId,
      sourceEntityId = sourceEntityId,
      attackId = attackId
  )
}
