package net.bestia.zone.ai.goal.consideration

import org.springframework.stereotype.Service

/**
 * Resolves [ResponseCurve]s by their id, indexed with the `associateBy` pattern used across the
 * codebase (all beans injected as a list, keyed by id).
 */
@Service
class CurveRegistry(
  curves: List<ResponseCurve>
) {

  private val curvesById = curves.associateBy { it.id }

  fun has(id: String): Boolean = curvesById.containsKey(id)

  fun get(id: String): ResponseCurve =
    curvesById[id] ?: throw IllegalArgumentException("Unknown response curve '$id'")
}
