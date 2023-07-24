package net.bestia.zoneserver.script

import net.bestia.model.battle.Attack
import net.bestia.model.battle.AttackTarget
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.entity.Entity

sealed class ScriptExec {
    abstract val callbackContext: CallbackContext?
}

abstract class ItemScriptExec : ScriptExec() {
    abstract val itemDatabaseName: String
    abstract val userEntityId: Long
}

data class ItemEntityScriptExec(
    override val itemDatabaseName: String,
    override val userEntityId: Long,
    override val callbackContext: CallbackContext? = null,
    val targetEntityId: Long
) : ItemScriptExec()

data class ItemLocationScriptExec(
    override val itemDatabaseName: String,
    override val userEntityId: Long,
    override val callbackContext: CallbackContext? = null,
    val targetPosition: Vec3
) : ItemScriptExec()

abstract class AttackScriptExec : ScriptExec() {
    abstract val attackerEntityId: Long
    abstract val attack: Attack
}

data class AttackEntityScriptExec(
    override val attackerEntityId: Long,
    override val callbackContext: CallbackContext? = null,
    override val attack: Attack,
    val targetEntityId: Long
) : AttackScriptExec()

data class AttackLocationScriptExec(
    override val attackerEntityId: Long,
    override val callbackContext: CallbackContext? = null,
    override val attack: Attack,
    val targetPosition: Vec3
) : AttackScriptExec()