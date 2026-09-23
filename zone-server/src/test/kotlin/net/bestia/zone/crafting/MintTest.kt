package net.bestia.zone.crafting

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.crafting.CraftingFixture.Companion.COIN_ITEM_ID
import net.bestia.zone.crafting.CraftingFixture.Companion.INPUT_ITEM
import net.bestia.zone.crafting.CraftingFixture.Companion.SKILL_ID
import net.bestia.zone.crafting.CraftingFixture.Companion.recipe
import net.bestia.zone.crafting.CraftingFixture.Companion.stack
import net.bestia.zone.ecs.crafting.Crafting
import net.bestia.zone.ecs.item.ObtainItemIntent
import net.bestia.zone.economy.CoinReserve
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Minting is the only way coin enters the world, so it is the one craft that can be refused because the
 * world itself has run out - and the one whose output has to be taken off the reserve rather than made.
 */
class MintTest {

  private val mint = recipe(
    id = 1,
    identifier = "GOLD_COIN",
    output = Recipe.ItemStack(itemId = COIN_ITEM_ID, amount = COINS),
    inputs = listOf(Recipe.ItemStack(itemId = INPUT_ITEM, amount = 1)),
  )

  @Test
  fun `minting takes its coin off the reserve`() {
    val purse = CountingReserve(balance = COINS * 4.0)
    val fixture = CraftingFixture(listOf(mint), reserve = purse)
    val crafter = minter(fixture)

    fixture.service.resolve(fixture.world, crafter, Crafting(recipeId = mint.id, targetUniqueId = 0, totalSeconds = 1f))

    assertEquals(COINS.toDouble(), purse.charged, "the coin was struck without being taken off the reserve")
    val granted = fixture.world.get(crafter, ObtainItemIntent.CreateItemIntent::class)
    assertNotNull(granted)
    assertEquals(COINS, granted!!.amount)
  }

  @Test
  fun `a world with no gold left refuses, and the bar survives`() {
    val purse = CountingReserve(balance = COINS - 1.0)
    val fixture = CraftingFixture(listOf(mint), reserve = purse)
    val crafter = minter(fixture)

    fixture.service.resolve(fixture.world, crafter, Crafting(recipeId = mint.id, targetUniqueId = 0, totalSeconds = 1f))

    assertNull(
      fixture.world.get(crafter, ObtainItemIntent.CreateItemIntent::class),
      "coin was minted out of a reserve that could not cover it"
    )
    // Inputs are spent before the roll and never returned, so the refusal has to come first - otherwise
    // the bar's gold leaves the world without going back to the reserve and the supply quietly shrinks.
    assertEquals(
      BARS_HELD,
      fixture.inventoryOf(crafter).getItems().single { it.itemId == INPUT_ITEM }.amount,
      "a bar was consumed by a mint that was refused"
    )
    assertEquals(0.0, purse.charged)
  }

  @Test
  fun `an ordinary craft never touches the reserve`() {
    val forge = recipe(id = 2, identifier = "PRODUCE_ONE")
    val purse = CountingReserve(balance = 0.0)
    val fixture = CraftingFixture(listOf(forge), reserve = purse)
    val crafter = minter(fixture)

    fixture.service.resolve(fixture.world, crafter, Crafting(recipeId = forge.id, targetUniqueId = 0, totalSeconds = 1f))

    assertNotNull(
      fixture.world.get(crafter, ObtainItemIntent.CreateItemIntent::class),
      "an empty reserve blocked a craft that makes no money"
    )
    assertEquals(0.0, purse.charged)
  }

  private fun minter(fixture: CraftingFixture): Long {
    return fixture.givenCrafter(items = listOf(stack(INPUT_ITEM, BARS_HELD)), knownSkills = mapOf(SKILL_ID to 1))
  }

  private class CountingReserve(private val balance: Double) : CoinReserve {
    var charged: Double = 0.0

    override fun available(): Double {
      return balance
    }

    override fun charge(coins: Double) {
      charged += coins
    }
  }

  private companion object {
    const val COINS = 10_000

    /** More than one craft needs, so "the bar survives" is about the refusal and not about the stack. */
    const val BARS_HELD = 4
  }
}
