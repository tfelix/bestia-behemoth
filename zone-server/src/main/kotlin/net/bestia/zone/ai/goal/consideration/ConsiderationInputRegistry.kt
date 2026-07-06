package net.bestia.zone.ai.goal.consideration

import org.springframework.stereotype.Service

/**
 * Resolves a consideration input id to the [ConsiderationInput] bean that handles it. Because some
 * inputs cover a family of ids (e.g. every `trait_*`), resolution is by first matching handler
 * rather than an exact-id map. Add a new input by dropping in a new bean.
 */
@Service
class ConsiderationInputRegistry(
  private val inputs: List<ConsiderationInput>
) {

  fun has(inputId: String): Boolean = inputs.any { it.handles(inputId) }

  fun resolve(inputId: String): ConsiderationInput =
    inputs.firstOrNull { it.handles(inputId) }
      ?: throw IllegalArgumentException("Unknown consideration input '$inputId'")
}
