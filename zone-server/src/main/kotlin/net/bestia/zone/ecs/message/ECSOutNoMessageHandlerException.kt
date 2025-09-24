package net.bestia.zone.ecs.message

import net.bestia.zone.BestiaException

class ECSOutNoMessageHandlerException(clazz: Class<out OutECSMessage>) : BestiaException(
  code = "NO_ECS_OUT_HANDLER",
  message = "No ECS handler was found for message type: $clazz"
)
