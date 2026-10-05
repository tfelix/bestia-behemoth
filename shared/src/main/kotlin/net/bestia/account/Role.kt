package net.bestia.account

/**
 * A role bundles a set of [Authority]s. Roles are carried inside the login JWT (as the `role`
 * claim) and translated into the concrete authorities when the token is validated on the zone.
 */
enum class Role(val authorities: Set<Authority>) {
  USER(emptySet()),
  GM(
    setOf(
      Authority.ITEM,
      Authority.MAP_MOVE,
      Authority.KILL,
      Authority.EXP,
      Authority.SPAWN,
      Authority.TERRAIN,
      Authority.DIALOG,
      Authority.WORLD_TIME,
      Authority.KICK,
      Authority.BAN
    )
  ),
  SUPER_GM(Authority.entries.toSet());

  /**
   * Whether an action of this role may target an account of [other] role. Only a lower rank, so GMs cannot act
   * against each other. The declaration order above is the rank.
   */
  fun outranks(other: Role): Boolean {
    return ordinal > other.ordinal
  }
}
