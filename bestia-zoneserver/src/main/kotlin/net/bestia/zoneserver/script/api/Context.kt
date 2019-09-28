package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.actor.routing.MessageApi

interface Context {
  fun commitEntityUpdates(messageApi: MessageApi)
}