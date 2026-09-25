package net.bestia.zone.account.master.skill

import net.bestia.zone.BestiaEvent
import net.bestia.zone.util.EntityId

/**
 * A master's learned skills have moved: what they qualify for may have changed with them.
 *
 * Published by [MasterSkillTreeService] once an investment has been written to both the database and the
 * entity, and listened for by `EquipmentRevalidationService`, which takes off novice-only gear the master has
 * just outgrown.
 *
 * An event rather than a direct call because spending a skill point has no business knowing that equipment
 * exists. A compile-time dependency from `account.master.skill` on `item.equip` would put the gear rule in the
 * wrong package, and the next thing that moves a master's skills - a respec, a GM reset - would then have to
 * be taught about gear as well instead of just publishing this.
 *
 * Carries [entityId] alongside [masterId] because the listener needs the live entity and the publisher has
 * already resolved it; looking it up again would be a database round trip for something already in hand.
 */
class MasterSkillsChangedEvent(
  source: Any,
  val masterId: Long,
  val entityId: EntityId
) : BestiaEvent(source)
