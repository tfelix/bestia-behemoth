package net.bestia.model.item

data class ResourceEntry(
    val resource: Resource,
    val amount: Int
) {
  init {
    require(amount in 0..MAX_RESOURCE_AMOUNT_PER_SLOT) { "amount must be between 0 and 100" }
  }

  companion object {
    const val MAX_RESOURCE_AMOUNT_PER_SLOT = 100
  }
}