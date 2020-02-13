package net.bestia.zoneserver.script

import net.bestia.messages.entity.EntityMessage

class ScriptCommandProcessorTestInterceptor : ScriptCommandProcessor {
  val lastIssuedCommands = mutableListOf<EntityMessage>()

  override fun processCommands(commands: List<EntityMessage>) {
    lastIssuedCommands.clear()
    lastIssuedCommands.addAll(commands)
  }
}