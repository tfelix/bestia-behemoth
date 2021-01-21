package net.bestia.zoneserver.environment

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class MapFogOverlayTest {

  @Test
  fun `clear clears the whole overlay structure`() {
    val sut = MapFogOverlay(4000000000,5000000000)
    sut.clear()

    // TODO Add the request
  }
}