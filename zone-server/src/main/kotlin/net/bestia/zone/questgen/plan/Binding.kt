package net.bestia.zone.questgen.plan

import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.fact.FactRef
import net.bestia.zone.text.TextArg

/** One real thing of the world a step can point at. */
data class Binding(
  val target: WorldTarget,
  /** Where it is, for the length of the walk. */
  val at: Vec2d,
  /** Names for the texts of the quest, such as the town a grave lies in. */
  val args: Map<String, TextArg> = emptyMap(),
  /** What a master knows after the step. */
  val teaches: List<FactRef> = emptyList(),
  /** The item a bring or deliver step is about. */
  val itemId: Long? = null,
  /** How many of it the step asks for. */
  val amount: Int = 1,
)
