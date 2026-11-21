package net.bestia.zone.stronghold

/** The two watchers a Stronghold can call, by their species. In the proto's order. */
enum class WatcherKind(val species: String) {
  MELEE("melee_guardian"),
  ARCHER("archer_guardian"),
}
