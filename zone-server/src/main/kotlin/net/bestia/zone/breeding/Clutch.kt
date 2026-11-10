package net.bestia.zone.breeding

import net.bestia.zone.battle.status.IndividualValues
import net.bestia.zone.bestia.Sex
import java.time.Duration

/**
 * What an egg will hatch into. [attackLearnLevels] are the father's attacks that landed: skill id to the level the
 * offspring learns it at, ahead of or outside its species' learnset.
 */
class Clutch(
  val speciesId: Long,
  val sex: Sex,
  val ivs: IndividualValues,
  val attackLearnLevels: Map<Long, Int>,
  val hatchTime: Duration,
)
