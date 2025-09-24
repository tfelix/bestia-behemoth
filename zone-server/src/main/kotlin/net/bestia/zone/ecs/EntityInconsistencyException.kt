package net.bestia.zone.ecs

import net.bestia.zone.BestiaException

/**
 * Quite a serious exception which is thrown at multiple places where an inconsistent mismatch
 * between entity ids or session ids from the ECS and outside occurred. Certainly a bad thing and
 * should ge fixed as those errors can not really be recovered.
 */
class EntityInconsistencyException(message: String) : BestiaException(
  code = "ENTITY_INCONSISTENCY",
  message = message
)