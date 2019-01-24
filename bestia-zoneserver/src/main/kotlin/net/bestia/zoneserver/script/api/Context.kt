package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.MessageApi

interface Context {
  fun commitEntityUpdates(messageApi: MessageApi)
}