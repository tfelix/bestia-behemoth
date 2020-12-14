package net.bestia.ai

class Consideration

// Gather available actions
interface Precondition
interface Effect

/*
Goal: Eat
Gather options:
- From Environment: UseTable -> GetFood, GetWater -> Goto Cupboard -> GetFoot -> Goto Flask ->
- From Inventory: Item: Snack -> GetFromInventory(c: 1) -> Use(Hunger -5)
 */

interface EatAction {
  fun preconditions(): Set<Precondition>
  fun effect(): Set<Effect>
}

