package net.bestia.zone.ecs.message

import net.bestia.zone.BestiaException

class ECSInNoMessageHandlerException(clazz: Class<out InECSMessage>) : BestiaException(
  code = "NO_ECS_IN_HANDLER",
  message = "No ECS handler was found for message type: $clazz"
)