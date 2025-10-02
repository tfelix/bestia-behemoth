package net.bestia.zone.ecs

import net.bestia.zone.BestiaException

class ComponentNotFoundException(type: Class<out Component>) : BestiaException(
  code = "COMP_NOT_FOUND",
  message = "Component $type was not found on entity"
)