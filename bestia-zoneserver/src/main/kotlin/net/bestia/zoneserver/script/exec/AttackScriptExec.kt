package net.bestia.zoneserver.script.exec

import net.bestia.model.battle.Attack
import net.bestia.model.battle.AttackTarget
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.script.ScriptKeyBuilder
import net.bestia.zoneserver.script.ScriptType

data class AttackScriptExec private constructor(
    override val scriptKey: String,
    val userId: Long,
    val targetId: Long?,
    val targetPosition: Vec3?,
    val attack: Attack
) : ScriptExec {

  override fun setupEnvironment(bindings: MutableMap<String, Any?>) {
    bindings["OWNER"] = userId
    bindings["TARGET_ENTITY"] = targetId
    bindings["TARGET_POSITION"] = targetPosition
    bindings["ATTACK"] = attack
  }

  class Builder(
      val attack: Attack,
      val owner: Entity
  ) {
    var targetEntity: Entity? = null
    var targetPoint: Vec3? = null

    fun build(): AttackScriptExec {
      require(!((attack.target == AttackTarget.ENEMY_ENTITY || attack.target == AttackTarget.FRIENDLY_ENTITY)
          && targetEntity == null)) { "Attack needs a target entity but target entity not set" }
      require(attack.target == AttackTarget.GROUND && targetPoint != null) { "Attack needs a target point but target point not set" }

      return AttackScriptExec(
          scriptKey = ScriptKeyBuilder.getScriptKey(ScriptType.ATTACK, attack.databaseName),
          userId = owner.id,
          targetId = targetEntity?.id,
          targetPosition = targetPoint,
          attack = attack
      )
    }
  }
}