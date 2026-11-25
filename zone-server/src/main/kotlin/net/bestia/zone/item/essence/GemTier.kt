package net.bestia.zone.item.essence

/**
 * The five gems that can hold an essence. The gem alone sets an essence's strength, so "species + gem" is
 * everything a buyer needs to know about one. A stronger Bestia only opens the better gems.
 */
enum class GemTier(
  val points: Int,
  val minBestiaLevel: Int,
  val minItemLevel: Int,
  val cutGem: String,
  /** The highest attack level an essence in this gem casts, as Spell Inscription caps it. */
  val attackLevelCap: Int,
) {
  AMETHYST(points = 3, minBestiaLevel = 1, minItemLevel = 0, cutGem = "cut_amethyst", attackLevelCap = 20),
  EMERALD(points = 5, minBestiaLevel = 21, minItemLevel = 1, cutGem = "cut_emerald", attackLevelCap = 40),
  RUBY(points = 8, minBestiaLevel = 41, minItemLevel = 21, cutGem = "cut_ruby", attackLevelCap = 60),
  PYRELITH(points = 11, minBestiaLevel = 61, minItemLevel = 41, cutGem = "cut_pyrelith", attackLevelCap = 80),
  DIAMOND(points = 15, minBestiaLevel = 81, minItemLevel = 61, cutGem = "cut_diamond", attackLevelCap = Int.MAX_VALUE);

  companion object {
    fun ofCutGem(identifier: String): GemTier? {
      return entries.find { it.cutGem == identifier }
    }

    /** The one gem a boss fills. Its essence is fixed, so a better gem would add nothing. */
    fun ofBossLevel(bossLevel: Int): GemTier {
      return when {
        bossLevel <= 60 -> RUBY
        bossLevel <= 80 -> PYRELITH
        else -> DIAMOND
      }
    }
  }
}
