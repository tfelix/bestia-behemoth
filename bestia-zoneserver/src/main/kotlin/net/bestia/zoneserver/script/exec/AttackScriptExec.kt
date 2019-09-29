package net.bestia.zoneserver.script.exec

import net.bestia.model.battle.Attack
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.script.ScriptKeyBuilder
import net.bestia.zoneserver.script.ScriptType

data class AttackScriptExec private constructor(
    override val scriptKey: String,
    override val callbackFunction: String?,
    val userId: Long,
    val targetId: Long?,
    val targetPosition: Vec3?,
    val attack: Attack,
    val attackLevel: Int
) : ScriptExec {

  override fun setupEnvironment(bindings: MutableMap<String, Any?>) {
    bindings["SELF"] = userId
    bindings["TARGET_ENTITY"] = targetId
    bindings["TARGET_POSITION"] = targetPosition
    bindings["ATTACK"] = attack
    bindings["ATTACK_LV"] = attackLevel
  }

  class Builder() {
    var attack: Attack? = null
    var usedAttackLevel: Int? = null
    var user: Entity? = null
    var targetEntity: Entity? = null
    var targetPoint: Vec3? = null

    fun build(): AttackScriptExec {
      require(attack != null) { "Attack must be given" }
      require(user != null) { "User entity must not be null" }

      return AttackScriptExec(
          scriptKey = ScriptKeyBuilder.getScriptKey(ScriptType.ATTACK, attack!!.databaseName),
          callbackFunction = null,
          userId = user!!.id,
          targetId = targetEntity?.id,
          targetPosition = targetPoint,
          attack = attack!!,
          attackLevel = usedAttackLevel!!
      )
    }
  }
}