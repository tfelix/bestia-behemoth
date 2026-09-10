package net.bestia.zone.ecs.spawn.townsfolk

/**
 * Who a townsperson is, independent of the entity that is currently standing in for them.
 *
 * An entity id cannot say it. Townsfolk are materialised when a player comes near and destroyed when
 * everyone leaves, so the baker who serves you in the morning is a different entity by the afternoon -
 * and anything remembered about a person has to key on something that survives that. `(settlement,
 * household, member)` does, because all three are indices into things the world generator derives the
 * same way every time: the settlement roster, `Households.one`, and the member list inside it.
 *
 * Packed into a `Long` in `PropId`'s shape and for `PropId`'s reason - it is the key type every stored
 * per-thing state in this server already uses.
 */
object TownsfolkIdentity {

  /** Settlements are dense from zero and a world holds a few hundred; a million is room enough. */
  private const val SETTLEMENT_BITS = 20

  /** A city runs to a few thousand households. Sixteen million is not close. */
  private const val HOUSEHOLD_BITS = 24

  /** `Households.membersOf` builds at most a handful. A byte is generous. */
  private const val MEMBER_BITS = 8

  private const val SETTLEMENT_LIMIT = 1 shl SETTLEMENT_BITS
  private const val HOUSEHOLD_LIMIT = 1 shl HOUSEHOLD_BITS
  private const val MEMBER_LIMIT = 1 shl MEMBER_BITS

  private const val HOUSEHOLD_SHIFT = MEMBER_BITS
  private const val SETTLEMENT_SHIFT = MEMBER_BITS + HOUSEHOLD_BITS

  fun of(settlement: Int, household: Int, member: Int): Long {
    require(settlement in 0 until SETTLEMENT_LIMIT) { "settlement $settlement does not fit $SETTLEMENT_BITS bits" }
    require(household in 0 until HOUSEHOLD_LIMIT) { "household $household does not fit $HOUSEHOLD_BITS bits" }
    require(member in 0 until MEMBER_LIMIT) { "member $member does not fit $MEMBER_BITS bits" }

    return (settlement.toLong() shl SETTLEMENT_SHIFT) or
      (household.toLong() shl HOUSEHOLD_SHIFT) or
      member.toLong()
  }

  fun settlementOf(id: Long): Int = (id ushr SETTLEMENT_SHIFT).toInt()

  fun householdOf(id: Long): Int = ((id ushr HOUSEHOLD_SHIFT) and (HOUSEHOLD_LIMIT - 1).toLong()).toInt()

  fun memberOf(id: Long): Int = (id and (MEMBER_LIMIT - 1).toLong()).toInt()

  fun describe(id: Long): String = "s${settlementOf(id)}/h${householdOf(id)}/m${memberOf(id)}"
}
