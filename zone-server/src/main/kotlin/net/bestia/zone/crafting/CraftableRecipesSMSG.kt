package net.bestia.zone.crafting

import net.bestia.bnet.proto.CraftableRecipesSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.crafting.discovery.CraftVector
import net.bestia.zone.entity.StaticEntityKind

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
  /** The skill the window shows first, or `CraftingService.NO_SKILL`. */
  val skillId: Long,

  /** The station the recipes are worked at, or null for Handiwork. */
  val station: StaticEntityKind?,

  val recipes: List<CraftableRecipe>,

  /** The player structure the recipes are worked at, or 0 for Handiwork. */
  val structureId: Long = 0,

  /** Seconds a campfire has left to burn; 0 once it is out or for a station that burns nothing. */
  val fuelSeconds: Long = 0,

  /** Other masters' Bestias offered at this Carving Table, for the etching picker. */
  val etchingOffers: List<EtchingOffer> = emptyList(),

  /** The crafter's own Bestias in storage, for the etching picker. */
  val storedBestias: List<StoredEtchable> = emptyList(),

  /** At a Carving Table, what is locked into it and what the viewer could lock in. */
  val carvingTable: CarvingTableContents.Contents? = null,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val protoRecipes = recipes.map { recipe ->
      CraftableRecipesSmsgProto.CraftableRecipe.newBuilder()
        .setRecipeId(recipe.recipeId)
        .setSkillId(recipe.skillId)
        .setEffect(recipe.effect.ordinal)
        .setOutputItemId(recipe.outputItemId)
        .setOutputAmount(recipe.outputAmount)
        .setSuccessPermille(recipe.successPermille)
        .setCraftMillis(recipe.craftMillis)
        .setReliableBestiaLevel(recipe.reliableBestiaLevel)
        .setInputsHeld(recipe.inputs.all { it.held >= it.amount })
        .addAllPoint(recipe.point?.toAxisList().orEmpty())
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
      .setStructureId(structureId)
      .setFuelSeconds(fuelSeconds.toInt())
      .addAllEtchingOffers(
        etchingOffers.map { offer ->
          CraftableRecipesSmsgProto.EtchingOffer.newBuilder()
            .setEntityId(offer.bestiaId)
            .setMobId(offer.speciesId.toInt())
            .setLevel(offer.level)
            .setOwnerName(offer.ownerName)
            .build()
        }
      )
      .addAllStoredBestias(
        storedBestias.map { stored ->
          CraftableRecipesSmsgProto.StoredEtchable.newBuilder()
            .setPlayerBestiaId(stored.playerBestiaId)
            .setMobId(stored.speciesId.toInt())
            .setLevel(stored.level)
            .setName(stored.name.orEmpty())
            .build()
        }
      )
      .addAllDeposited(carvingTable?.deposited.orEmpty().map { deposited ->
        CraftableRecipesSmsgProto.DepositedBestia.newBuilder()
          .setPlayerBestiaId(deposited.playerBestiaId)
          .setMobId(deposited.speciesId.toInt())
          .setLevel(deposited.level)
          .setName(deposited.name.orEmpty())
          .setOwnerName(deposited.ownerName)
          .setMine(deposited.mine)
          .build()
      })
      .addAllDepositable(carvingTable?.depositable.orEmpty().map { depositable ->
        CraftableRecipesSmsgProto.DepositableBestia.newBuilder()
          .setPlayerBestiaId(depositable.playerBestiaId)
          .setMobId(depositable.speciesId.toInt())
          .setLevel(depositable.level)
          .setName(depositable.name.orEmpty())
          .setInStorage(depositable.inStorage)
          .build()
      })
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCraftableRecipes(message)
      .build()
  }

  data class CraftableRecipe(
    val recipeId: Long,
    val skillId: Long,
    val effect: RecipeEffect,

    /** Both 0 for an effect that changes an item rather than making one. */
    val outputItemId: Int,
    val outputAmount: Int,

    val inputs: List<Input>,

    /** Per mille, so no float crosses the wire for a number shown as a percentage. */
    val successPermille: Int,

    /** After the crafter's construction-time reduction - the time they will actually wait. */
    val craftMillis: Int,

    /** Where the blueprint sits in the feature space, for the discovery chart's outline; null when it has none. */
    val point: CraftVector? = null,

    /**
     * For an etching, the highest Bestia level it binds at [successPermille]; each level above costs 5%.
     * [Int.MAX_VALUE] when no level is too high, 0 for any other recipe.
     */
    val reliableBestiaLevel: Int = 0
  )

  data class Input(
    val itemId: Int,
    val amount: Int,

    /** How many the crafter is holding right now, so the window can show "2/3". */
    val held: Int
  )
}
