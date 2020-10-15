package net.bestia.zoneserver.entity.component

import net.bestia.zoneserver.battle.attack.KnownAttack

/**
 * These attacks are checked if the bestia wants to perform an attack. It also
 * uses this information when calculating the AI reactions of the bestia.
 *
 * @author Thomas Felix
 */
data class AttackListComponent(
    override val entityId: Long,
    val knownAttacks: Set<KnownAttack>
) : Component