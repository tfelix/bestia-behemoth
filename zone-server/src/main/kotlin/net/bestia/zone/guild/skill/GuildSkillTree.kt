package net.bestia.zone.guild.skill

import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Service

/**
 * The guild skill tree from `guild_skill_tree.yml`, loaded at boot by
 * [net.bestia.zone.boot.GuildSkillTreeImporterBootRunner]. [load] refuses a tree a guild could get stuck in,
 * because the boot is where such a content mistake is cheapest to find.
 */
@Service
class GuildSkillTree {

  private var nodes: List<GuildSkillNode> = emptyList()
  private var nodesBySkillId: Map<Long, GuildSkillNode> = emptyMap()
  private var nodesByIdentifier: Map<String, GuildSkillNode> = emptyMap()

  fun load(nodes: List<GuildSkillNode>) {
    val bySkillId = nodes.associateBy { it.skillId }
    require(bySkillId.size == nodes.size) { "The guild skill tree lists a skill twice" }

    nodes.forEach { node -> requireReachable(node, bySkillId) }
    requireAcyclic(bySkillId)

    this.nodes = nodes
    nodesBySkillId = bySkillId
    nodesByIdentifier = nodes.associateBy { it.identifier }
  }

  /** In file order. */
  fun all(): List<GuildSkillNode> {
    return nodes
  }

  fun find(skillId: Long): GuildSkillNode? {
    return nodesBySkillId[skillId]
  }

  fun find(skill: SkillId): GuildSkillNode? {
    return nodesByIdentifier[skill.name]
  }

  fun mayRaise(node: GuildSkillNode, levelOf: (identifier: String) -> Int): Boolean {
    if (levelOf(node.identifier) >= node.maxLevel) {
      return false
    }

    return node.prerequisites.all { prerequisite ->
      val required = nodesBySkillId.getValue(prerequisite.skillId)
      levelOf(required.identifier) >= prerequisite.level
    }
  }

  private fun requireReachable(node: GuildSkillNode, bySkillId: Map<Long, GuildSkillNode>) {
    require(node.maxLevel >= 1) { "Guild skill ${node.identifier} has max level ${node.maxLevel}" }

    node.prerequisites.forEach { prerequisite ->
      val required = bySkillId[prerequisite.skillId]
        ?: error("Guild skill ${node.identifier} needs skill ${prerequisite.skillId}, which is not in the guild tree")
      require(prerequisite.level in 1..required.maxLevel) {
        "Guild skill ${node.identifier} needs ${required.identifier} Lv.${prerequisite.level}, " +
          "but it only goes to Lv.${required.maxLevel}"
      }
    }
  }

  private fun requireAcyclic(bySkillId: Map<Long, GuildSkillNode>) {
    val done = mutableSetOf<Long>()
    val onPath = mutableSetOf<Long>()

    fun visit(skillId: Long) {
      if (skillId in done) {
        return
      }
      require(onPath.add(skillId)) {
        "The guild skill tree has a cycle through ${bySkillId.getValue(skillId).identifier}"
      }
      bySkillId.getValue(skillId).prerequisites.forEach { visit(it.skillId) }
      onPath.remove(skillId)
      done.add(skillId)
    }

    bySkillId.keys.forEach { visit(it) }
  }
}
