package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.actor.entity.EntityEnvelope

interface EntityCommand {
  fun toEntityEnvelope(): EntityEnvelope
}