package net.bestia.zoneserver.script

import net.bestia.model.battle.Attack
import net.bestia.model.battle.AttackTarget
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.entity.Entity

sealed class ScriptContext {
    abstract fun toScriptExec(): ScriptExec
}

abstract class ItemScriptContext : ScriptContext() {
    abstract val itemDatabaseName: String
    abstract val userEntity: Entity
}

data class ItemEntityScriptContext(
    override val itemDatabaseName: String,
    override val userEntity: Entity,
    val targetEntity: Entity
) : ItemScriptContext() {
    override fun toScriptExec(): ScriptExec {
        return ItemEntityScriptExec(
            itemDatabaseName = itemDatabaseName,
            userEntityId = userEntity.id,
            targetEntityId = targetEntity.id
        )
    }
}

data class ItemLocationScriptContext(
    override val itemDatabaseName: String,
    override val userEntity: Entity,
    val targetPosition: Vec3
) : ItemScriptContext() {
    override fun toScriptExec(): ScriptExec {
        return ItemLocationScriptExec(
            itemDatabaseName = itemDatabaseName,
            userEntityId = userEntity.id,
            targetPosition = targetPosition
        )
    }
}

abstract class AttackScriptContext : ScriptContext() {
    abstract val attackerEntity: Entity
    abstract val attack: Attack
}

data class AttackEntityScriptContext(
    override val attackerEntity: Entity,
    val targetEntity: Entity,
    override val attack: Attack
) : AttackScriptContext() {
    override fun toScriptExec(): ScriptExec {
        return AttackEntityScriptExec(
            attackerEntityId = attackerEntity.id,
            attack = attack,
            targetEntityId = targetEntity.id
        )
    }
}

data class AttackLocationScriptContext(
    override val attackerEntity: Entity,
    val targetPosition: Vec3,
    override val attack: Attack
) : AttackScriptContext() {
    override fun toScriptExec(): ScriptExec {
        return AttackLocationScriptExec(
            attackerEntityId = attackerEntity.id,
            attack = attack,
            targetPosition = targetPosition
        )
    }
}