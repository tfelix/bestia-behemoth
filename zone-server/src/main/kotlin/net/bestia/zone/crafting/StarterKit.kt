package net.bestia.zone.crafting

import org.springframework.stereotype.Service

/**
 * The blueprints every master knows without discovering them, read from `starter_kit.yml`.
 *
 * Loaded once by [net.bestia.zone.boot.StarterKitImporterBootRunner] before the tick loop starts and only read
 * afterwards, like [RecipeRegistry].
 */
@Service
class StarterKit {

  private var blueprintIds: Set<Long> = emptySet()

  fun load(recipeIds: Set<Long>) {
    blueprintIds = recipeIds
  }

  fun isStarterBlueprint(recipeId: Long): Boolean = recipeId in blueprintIds
}
