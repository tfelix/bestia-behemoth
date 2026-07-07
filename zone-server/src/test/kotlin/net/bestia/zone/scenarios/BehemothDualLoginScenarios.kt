package net.bestia.zone.scenarios

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Disabled("Unimplemented scenario stubs (TODO()) - dual-login behavior not yet written")
class BehemothDualLoginScenarios : BestiaNoSocketScenario(
  autoClientConnect = false
) {

  @Test
  @Order(1)
  fun `connecting with the first bestia master of account 1 works`() {
    TODO()
  }

  @Test
  @Order(2)
  fun `connecting with the second bestia master of account 1 disconnects the first`() {
    TODO()
  }
}
