package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.actor.routing.MessageApi
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
class ScriptCommandSenderProcessor(
    private val messageApi: MessageApi
) : ScriptCommandProcessor {
  override fun processCommands(commands: List<EntityMessage>) {
    LOG.trace { "Sending ${commands.map { it.javaClass.simpleName }} commands to entities" }
    commands.forEach { messageApi.send(EntityEnvelope(entityId = it.entityId, content = it)) }
  }
}