package net.bestia.ai.blackboard

interface BlackboardRepository {
  fun findById(id: String): Blackboard?
  fun save(blackboard: Blackboard)
}