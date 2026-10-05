package net.bestia.login.webauthn

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional

/**
 * A challenge may be spent once. Two requests finishing the same ceremony at once both find its row, so only the
 * delete can tell them apart.
 */
class CeremonyStoreTest {

  private val ceremony = WebAuthnCeremony(
    id = CEREMONY,
    ceremonyType = CeremonyType.ASSERTION,
    requestJson = "{}",
    expiresAt = LocalDateTime.now().plusMinutes(1)
  )

  private val ceremonies = mockk<WebAuthnCeremonyRepository>(relaxed = true) {
    every { findById(CEREMONY) } returns Optional.of(ceremony)
  }

  private val store = CeremonyStore(ceremonies, mockk(relaxed = true))

  @Test
  fun `a ceremony another request already deleted is refused`() {
    every { ceremonies.deleteTaken(CEREMONY) } returns 0

    assertThrows(WebAuthnException::class.java) { store.take(CEREMONY, setOf(CeremonyType.ASSERTION)) }
  }

  private companion object {
    const val CEREMONY = "ceremony"
  }
}
