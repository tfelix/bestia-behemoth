package net.bestia.zone.crafting

import net.bestia.bnet.proto.CraftableRecipesSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.world.prop.StaticEntityKind

/**
 * What the caster can make with the skill they just activated, at the station they are standing next to.
 *
 * Account-scoped rather than entity-scoped, like [net.bestia.zone.dialog.DialogSMSG]: this opens a window, and
 * a window belongs to a client rather than to an entity in the world.
 *
 * Carries no names. A producing recipe is named by its output item, which the client resolves through its own
 * item DB; the three item-changing effects are named by the effect. That is what spares the recipe catalogue a
 * second localized copy on the client.
 */
data class CraftableRecipesSMSG(
  val skillId: Long,

  /** The station actually found in range, or null when none was needed or none was there. */
  val station: StaticEntityKind?,

  val recipes: List<CraftableRecipe>
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val protoRecipes = recipes.map { recipe ->
      CraftableRecipesSmsgProto.CraftableRecipe.newBuilder()
        .setRecipeId(recipe.recipeId)
        .setEffect(recipe.effect.ordinal)
        .setOutputItemId(recipe.outputItemId)
        .setOutputAmount(recipe.outputAmount)
        .setSuccessPermille(recipe.successPermille)
        .setCraftMillis(recipe.craftMillis)
        .setInputsHeld(recipe.inputs.all { it.held >= it.amount })
        .addAllInputs(
          recipe.inputs.map { input ->
            CraftableRecipesSmsgProto.RecipeInput.newBuilder()
              .setItemId(input.itemId)
              .setAmount(input.amount)
              .setHeld(input.held)
              .build()
          }
        )
        .build()
    }

    val message = CraftableRecipesSmsgProto.CraftableRecipesSMSG.newBuilder()
      .setSkillId(skillId)
      // Ordinal + 1, so 0 can mean "none" - the same encoding `ItemResource.equip_slot` uses on the client.
      .setStationKind(station?.let { it.ordinal + 1 } ?: 0)
      .addAllRecipes(protoRecipes)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCraftableRecipes(message)
      .build()
  }

  data class CraftableRecipe(
    val recipeId: Long,
    val effect: RecipeEffect,

    /** Both 0 for an effect that changes an item rather than making one. */
    val outputItemId: Int,
    val outputAmount: Int,

    val inputs: List<Input>,

    /** Per mille, so no float crosses the wire for a number shown as a percentage. */
    val successPermille: Int,

    /** After the crafter's construction-time reduction - the time they will actually wait. */
    val craftMillis: Int
  )

  data class Input(
    val itemId: Int,
    val amount: Int,

    /** How many the crafter is holding right now, so the window can show "2/3". */
    val held: Int
  )
}
