package net.bestia.zone.world.ground

/**
 * What every graded ground layer has to say about itself.
 *
 * Extracted when blood became the second one: the store below it is the same store, and the only things it
 * needs from a layer are how long it lasts, how faint it may get before it stops being sent, how often it is
 * written and how much of it is held at once. Everything that differs - what makes a mark and how much of it -
 * belongs to the writer, which is where the two layers actually disagree.
 *
 * @property fadeSeconds Bestia seconds for a full cell to fade to nothing untouched
 * @property visibleThreshold the level below which a cell is not sent at all
 * @property flushIntervalSeconds how often changed columns are written out
 * @property maxResidentColumns columns held in memory at once
 */
interface GroundLevelConfig {

  val fadeSeconds: Long

  val visibleThreshold: Int

  val flushIntervalSeconds: Float

  val maxResidentColumns: Int
}
