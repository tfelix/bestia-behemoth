package net.bestia.ai.behavior.tree

interface Node {
  fun tick(): NodeStatus
}