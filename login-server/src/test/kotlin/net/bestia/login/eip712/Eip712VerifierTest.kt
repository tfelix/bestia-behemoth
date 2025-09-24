package net.bestia.login.eip712

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class Eip712VerifierTest {

  private val config = Eip712Config(
    name = "Bestia Login",
    version = "1",
    chainId = 1L
  )

  private val sigFixture = Eip712SignatureFixture()
  private val verifier = Eip712Verifier(config)

  @Test
  fun `verifySignature returns Success for correct signature`() {
    val tokenIndex = 42L

    // Create payload with the signer's address
    val payload = Eip712Verifier.LoginPayload(sigFixture.address, tokenIndex)
    val signature = sigFixture.generateValidSignature(payload)

    val result = verifier.verifySignature(payload, signature)

    Assertions.assertTrue(result is Eip712Verifier.VerificationResult.Success)

    if (result is Eip712Verifier.VerificationResult.Success) {
      Assertions.assertEquals(sigFixture.address, result.wallet)
      Assertions.assertEquals(tokenIndex, result.tokenIndex)
    }
  }

  @Test
  fun `verifySignature returns Failure for wrong address`() {
    val tokenIndex = 42L

    // Generate signature with signer's address
    val correctPayload = Eip712Verifier.LoginPayload(sigFixture.address, tokenIndex)
    val signature = sigFixture.generateValidSignature(correctPayload)

    // Create payload with wrong wallet address (different from signer)
    val wrongWallet = "0x000000000000000000000000000000000000dead"
    val wrongPayload = Eip712Verifier.LoginPayload(wrongWallet, tokenIndex)

    val result = verifier.verifySignature(wrongPayload, signature)

    Assertions.assertTrue(result is Eip712Verifier.VerificationResult.Failure)

    if (result is Eip712Verifier.VerificationResult.Failure) {
      Assertions.assertTrue(result.error.contains("does not match"))
    }
  }

  @Test
  fun `verifySignature returns Failure for bad signature`() {
    val tokenIndex = 42L

    // Get the correct signer address
    val payload = Eip712Verifier.LoginPayload(sigFixture.address, tokenIndex)

    val badSignature = "0xdeadbeef" // Invalid signature

    val result = verifier.verifySignature(payload, badSignature)

    Assertions.assertTrue(result is Eip712Verifier.VerificationResult.Failure)
    if (result is Eip712Verifier.VerificationResult.Failure) {
      Assertions.assertTrue(result.error.contains("Signature recovery failed"))
    }
  }

  @Test
  fun `test signature generation and verification end-to-end`() {
    val tokenIndex = 123L

    // Create the actual payload with the correct signer address
    val payload = Eip712Verifier.LoginPayload(sigFixture.address, tokenIndex)
    val signature = sigFixture.generateValidSignature(payload)

    println("Generated signature: $signature")

    // Test that the signature verifies correctly
    val result = verifier.verifySignature(payload, signature)

    Assertions.assertTrue(result is Eip712Verifier.VerificationResult.Success)

    if (result is Eip712Verifier.VerificationResult.Success) {
      Assertions.assertEquals(sigFixture.address, result.wallet)
      Assertions.assertEquals(tokenIndex, result.tokenIndex)
    }
  }
}