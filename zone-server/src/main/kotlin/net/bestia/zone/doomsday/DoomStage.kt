package net.bestia.zone.doomsday

/** How far a doomsday has come, by the doom clock's progress where each stage begins. */
enum class DoomStage(val from: Double) {
  OMENS(0.0),
  ONSET(0.15),
  ESCALATION(0.40),
  CATACLYSM(0.90);

  companion object {
    /**
     * The stage at [progress], coming from [current]. A threshold only counts once the progress is [hysteresis]
     * past it, so a clock hovering at a border does not flip the stage back and forth.
     */
    fun of(progress: Double, current: DoomStage, hysteresis: Double): DoomStage {
      return entries.last { stage ->
        val margin = if (stage.ordinal > current.ordinal) hysteresis else -hysteresis
        stage == OMENS || progress >= stage.from + margin
      }
    }
  }
}
