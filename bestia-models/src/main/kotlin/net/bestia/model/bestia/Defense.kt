package net.bestia.model.bestia

data class Defense(
    /**
     * Sets the defense. Must be between 0 and 1000 (which increments in
     * 1/10) percents.
     */
    val physicalDefense: Int = 0,
    /**
     * Sets the magic defense. Must be between 0 and 1000 (which increments in
     * 1/10) percents.
     */
    val magicDefense: Int = 0
) {
  init {
    require(physicalDefense >= 0)
    require(magicDefense >= 0)
  }

  operator fun plus(rhs: Defense): Defense {
    return Defense(
        physicalDefense = physicalDefense + rhs.physicalDefense,
        magicDefense = magicDefense + rhs.magicDefense
    )
  }
}