package net.bestia.ai

data class Consideration(
    val name: String,
    val amount: Int
)

// Gather available actions
interface Precondition
interface Effect

/*
Goal: Eat
Gather options:
- From Environment: UseTable -> GetFood, GetWater -> Goto Cupboard -> GetFoot -> Goto Flask ->
- From Inventory: Item: Snack -> GetFromInventory(c: 1) -> Use(Hunger -5)
 */

