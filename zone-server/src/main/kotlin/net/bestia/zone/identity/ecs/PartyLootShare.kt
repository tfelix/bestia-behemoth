package net.bestia.zone.identity.ecs

/** How a party holds the drops of a kill it won. Stored on the party row by name, so the names are part of the schema. */
enum class PartyLootShare {
  /** Every member may pick up every drop. */
  PARTY_SHARE,

  /** Each drop is held for the next member near the kill, before the whole party may pick it up. */
  TAKE_TURNS,
}
