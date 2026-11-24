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
) {
  AMETHYST(points = 3, minBestiaLevel = 1, minItemLevel = 0, cutGem = "cut_amethyst"),
  EMERALD(points = 5, minBestiaLevel = 21, minItemLevel = 1, cutGem = "cut_emerald"),
  RUBY(points = 8, minBestiaLevel = 41, minItemLevel = 21, cutGem = "cut_ruby"),
  PYRELITH(points = 11, minBestiaLevel = 61, minItemLevel = 41, cutGem = "cut_pyrelith"),
  DIAMOND(points = 15, minBestiaLevel = 81, minItemLevel = 61, cutGem = "cut_diamond");

  companion object {
    fun ofCutGem(identifier: String): GemTier? {
      return entries.find { it.cutGem == identifier }
    }
  }
}
