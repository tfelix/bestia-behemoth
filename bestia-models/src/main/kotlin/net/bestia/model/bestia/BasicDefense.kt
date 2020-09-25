package net.bestia.model.bestia

data class BasicDefense(
    /**
     * Sets the defense. Must be between 0 and 1000 (which increments in
     * 1/10) percents.
     */
    override val physicalDefense: Int = 0,
    /**
     * Sets the magic defense. Must be between 0 and 1000 (which increments in
     * 1/10) percents.
     */
    override val magicDefense: Int = 0
) : Defense {
  operator fun plus(rhs: Defense): BasicDefense {
    return BasicDefense(
        physicalDefense = physicalDefense + rhs.physicalDefense,
        magicDefense = magicDefense + rhs.magicDefense
    )
  }
}