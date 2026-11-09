package net.bestia.zone.structure

/**
 * How well a material carries itself away from the ground.
 *
 * A piece on the ground has a stability of 1. A piece resting on or hanging from another has that one's stability
 * less [verticalLoss], and a piece held only from the side has it less [horizontalLoss]. Below [minStability] it
 * cannot stand.
 */
data class StructureMaterial(
  val name: String,
  val verticalLoss: Double,
  val horizontalLoss: Double,
  val minStability: Double
)
