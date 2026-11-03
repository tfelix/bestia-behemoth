package net.bestia.zone.honor.ecs

import net.bestia.zone.ecs.core.Component

/** The accounts a building already charged for damaging it, so one long siege costs once per attacker. */
class ChargedVandals : Component {
  val accounts: MutableSet<Long> = HashSet()
}
