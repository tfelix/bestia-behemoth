package net.bestia.zoneserver.script

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import net.bestia.zoneserver.script.api.BestiaApiFactory

class ScriptExecutionActor private constructor(
    context: ActorContext<ExecuteScript>,
    private val apiFactory: BestiaApiFactory,
    private val scriptCache: ScriptCache
) : AbstractBehavior<ScriptExecutionActor.ExecuteScript>(context) {

    sealed class Command

    data class ExecuteScript(
        val scriptContext: ScriptContext,
    ) : Command()

    data class ExecuteScriptCallback(
        val callback: String,
        val scriptContext: ScriptContext
    ) : Command()

    override fun createReceive(): Receive<ExecuteScript> {
        return newReceiveBuilder().onMessage(ExecuteScript::class.java, ::onExecuteScript).build()
    }

    private fun onExecuteScript(command: ExecuteScript): Behavior<ExecuteScript> {
        val script = scriptCache.getScriptInstance(command.scriptContext)
        val api = apiFactory.buildScriptRootApi(command.scriptContext)
        val scriptExec = command.scriptContext.toScriptExec()

        script.execute(api, scriptExec)

        processGeneratedCommands(api.messages)

        return Behaviors.stopped()
    }

    private fun processGeneratedCommands(messages: List<ScriptMessage>) {
        messages.forEach { command ->
            when (command) {
                is ScriptQuery -> handleScriptQuery(command)
                is EntityMessage -> TODO()
            }
        }
    }

    private fun handleScriptQuery(message: ScriptQuery) {
        when (message) {
            is QueryEntity -> TODO()
        }
    }

    companion object {
        fun create(
            apiFactory: BestiaApiFactory,
            scriptCache: ScriptCache
        ): Behavior<ExecuteScript> {
            return Behaviors.setup { context -> ScriptExecutionActor(context, apiFactory, scriptCache) }
        }
    }
}