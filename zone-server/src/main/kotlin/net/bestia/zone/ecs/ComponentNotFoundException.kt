package net.bestia.zone.ecs

import com.github.quillraven.fleks.ComponentType
import net.bestia.zone.BestiaException

class ComponentNotFoundException(type: ComponentType<*>) : BestiaException(
  code = "COMP_NOT_FOUND",
  message = "Component $type was not found on entity"
)