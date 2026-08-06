package net.bestia.zone.dialog

import org.springframework.stereotype.Service

/**
 * In-memory store of the dialog catalog, keyed by dialog id. Populated once at boot by
 * [net.bestia.zone.boot.DialogImporterBootRunner] from `dialogs.yml`; the catalog is config, not
 * player state, so it is never persisted to the database (mirrors
 * [net.bestia.zone.battle.status.StatusEffectDefinitionRegistry]).
 */
@Service
class DialogDefinitionRegistry {

  private var definitionsById: Map<Int, DialogDefinition> = emptyMap()

  fun load(definitions: List<DialogDefinition>) {
    definitionsById = definitions.associateBy { it.id }
  }

  fun findById(id: Int): DialogDefinition? = definitionsById[id]

  fun findByIdentifier(identifier: String): DialogDefinition? =
    definitionsById.values.firstOrNull { it.identifier.equals(identifier, ignoreCase = true) }

  fun getOrThrow(id: Int): DialogDefinition =
    findById(id) ?: throw DialogDefinitionNotFoundException(id)

  fun getOrThrow(dialog: DialogId): DialogDefinition = getOrThrow(dialog.id)

  fun all(): Collection<DialogDefinition> = definitionsById.values
}
