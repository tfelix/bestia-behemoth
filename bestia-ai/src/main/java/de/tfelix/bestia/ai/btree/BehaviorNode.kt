package de.tfelix.bestia.ai.btree

abstract sealed class BehaviorResult
object BehaviorSuccess : BehaviorResult()
data class BehaviorRunning(
    val nodePath: String
) : BehaviorResult()

object BehaviorFailed : BehaviorResult()

interface BehaviorNode {
  fun run(nodePath: String): BehaviorResult
}

class BehaviorRoot {

}

class BehaviorSequence(
    private val nodes: List<BehaviorNode>
) : BehaviorNode {
  override fun run(nodePath: String): BehaviorResult {
    val path = nodePath.split("/", ignoreCase = true, limit = 0)
    // ist es ein pfad oder ein direkter pointer?

    return BehaviorFailed
  }

}