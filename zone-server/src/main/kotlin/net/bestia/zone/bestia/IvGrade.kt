package net.bestia.zone.bestia

/** How an individual value reads to a master without Bloodline Study. The order is the wire order. */
enum class IvGrade(private val lowest: Int) {
  POOR(0),
  FAIR(20),
  GOOD(40),
  GREAT(60),
  EXCELLENT(80),
  PERFECT(95);

  companion object {
    fun of(iv: Int): IvGrade {
      return entries.last { iv >= it.lowest }
    }
  }
}
