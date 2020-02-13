package net.bestia.zoneserver.script

import net.bestia.messages.entity.EntityMessage

interface ScriptCommandProcessor {
  fun processCommands(commands: List<EntityMessage>)
}