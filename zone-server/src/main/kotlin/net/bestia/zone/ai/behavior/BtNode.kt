package net.bestia.zone.ai.behavior

/**
 * A node in a behaviour tree. A GOAP action supplies a small tree that the act stage ticks every
 * frame while that action is current. Trees here are stateless with respect to ticks (they
 * re-evaluate from [BtContext] each call), so they can be re-ticked safely.
 */
interface BtNode {
  fun tick(context: BtContext): Status
}

enum class Status {
  SUCCESS,
  FAILURE,
  RUNNING
}
