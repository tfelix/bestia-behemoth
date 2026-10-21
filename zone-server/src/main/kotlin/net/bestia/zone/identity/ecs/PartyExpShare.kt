package net.bestia.zone.identity.ecs

/** How a party shares kill EXP. Stored on the party row by name, so the names are part of the schema. */
enum class PartyExpShare {
  /** Every attacker keeps the EXP its own damage earned. */
  EACH_TAKE,

  /** An attacker's EXP gets a bonus and is split evenly among the members near the kill. */
  EVEN_SHARE,
}
