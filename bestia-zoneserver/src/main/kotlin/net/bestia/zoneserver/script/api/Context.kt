package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.actor.MessageApi

interface Context {
  fun commitEntityUpdates(messageApi: MessageApi)
}