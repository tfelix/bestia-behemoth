package net.bestia.zoneserver.entity.component

/**
 * These attacks are checked if the bestia wants to perform an attack. It also
 * uses this information when calculating the AI reactions of the bestia.
 *
 * @author Thomas Felix
 */
data class AttackListComponent(
        override  val id: Long,
        override val entityId: Long
) : Component {
  val knownAttacks = mutableSetOf<Int>()
}