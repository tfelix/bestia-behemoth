package net.bestia.messages.account

import net.bestia.messages.EntityMessage

/**
 * Lists the current learned attacks of an bestia. The attacks are sorted in the
 * order of the minimum level in order to use them. The attacks of the currently
 * selected bestia are returned.
 *
 * @author Thomas Felix
 */
data class AttackListResponse(
    override val entityId: Long,
    val attacks: List<AvailableAttack>
) : EntityMessage {
  data class AvailableAttack(
      val level: Int,
      val attackId: Long
  )
}