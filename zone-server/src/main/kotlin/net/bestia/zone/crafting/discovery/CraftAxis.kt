package net.bestia.zone.crafting.discovery

/**
 * One axis of the crafting feature space. Each runs from a negative pole through 0 to a positive pole, so an axis
 * that does not matter to an item sits near 0. See "The Feature Space Axes" in the design docs.
 */
enum class CraftAxis(val negativePole: String, val positivePole: String) {
  STATE("fluid", "solid"),
  INTEGRITY("brittle", "resilient"),
  HEFT("light", "massive"),
  EDGE("blunt", "keen"),
  VITALITY("necrotic", "restorative"),
  ARCANE("warding", "charged"),
  COMPLEXITY("crude", "intricate"),
}
