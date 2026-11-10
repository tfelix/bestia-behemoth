package net.bestia.zone.item.profession

import net.bestia.zone.ecs.core.Component

/**
 * The professions a master holds now, strongest first. Kept rather than derived on demand because a tie between
 * sub-trees is decided by what the master already held, see [ProfessionResolver].
 */
class Professions(
  var held: List<Profession> = emptyList(),
) : Component {

  val allowance: GearAllowance
    get() = GearAllowance.of(held)
}
