package net.bestia.login.eip712

import io.mockk.*
import net.bestia.login.account.Account
import net.bestia.login.account.AccountRepository
import net.bestia.login.jwt.JwtService
import net.bestia.login.ethereum.EthereumService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime
import java.util.Optional

class Eip712AuthenticationServiceTest {

  private val eip712Verifier = mockk<Eip712Verifier>()
  private val ethereumService = mockk<EthereumService>()
  private val jwtService = mockk<JwtService>()
  private val accountRepository = mockk<AccountRepository>()

  private lateinit var loginService: Eip712AuthenticationService

  private val testWallet = "0x1234567890123456789012345678901234567890"
  private val testTokenId = 123L
  private val testSignature =
    "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890ab"
  private val testJwtToken =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI0MiIsInRva2VuSWQiOjEyMywid2FsbGV0IjoiMHgxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwIn0.test-signature"
  private val testJwtHash = "abc123def456"
  private val testExpirationDate = LocalDateTime.now().plusDays(5)
  private val testRefreshToken = JwtService.RefreshToken(testJwtToken, testJwtHash, testExpirationDate)

  @BeforeEach
  fun setUp() {
    clearAllMocks()
    loginService = Eip712AuthenticationService(
      eip712Verifier,
      ethereumService,
      jwtService,
      accountRepository,
    )
  }

  @Test
  fun `successful login flow should return Success with JWT token and account ID`() {
    // Given
    val request = Eip712AuthenticationController.AuthRequest(testWallet, testTokenId, testSignature)
    val testAccount = Account(testTokenId)

    every {
      eip712Verifier.verifySignature(
        Eip712Verifier.LoginPayload(testWallet, testTokenId),
        testSignature
      )
    } returns Eip712Verifier.VerificationResult.Success(testWallet, testTokenId)

    every { ethereumService.verifyNftOwnership(testWallet, testTokenId) } returns true
    every { accountRepository.findByNftTokenId(testTokenId) } returns Optional.of(testAccount)
    every { accountRepository.save(any<Account>()) } returns testAccount
    every { jwtService.createRefreshToken(testAccount.id, testWallet, testTokenId) } returns testRefreshToken

    // When
    val result = loginService.authenticate(request)

    // Then
    assertTrue(result is Eip712AuthenticationService.AuthResult.Success)
    val successResult = result as Eip712AuthenticationService.AuthResult.Success
    assertEquals(testJwtToken, successResult.jwtToken)
    assertEquals(testAccount.id, successResult.accountId)

    verify(exactly = 1) {
      eip712Verifier.verifySignature(
        Eip712Verifier.LoginPayload(testWallet, testTokenId),
        testSignature
      )
    }
    verify(exactly = 1) { ethereumService.verifyNftOwnership(testWallet, testTokenId) }
    verify(exactly = 1) { accountRepository.findByNftTokenId(testTokenId) }
    verify(exactly = 1) { jwtService.createRefreshToken(testAccount.id, testWallet, testTokenId) }
  }

  @Test
  fun `successful login flow should create new account if none exists`() {
    // Given
    val request = Eip712AuthenticationController.AuthRequest(testWallet, testTokenId, testSignature)
    val newAccount = Account(testTokenId)

    every {
      eip712Verifier.verifySignature(
        Eip712Verifier.LoginPayload(testWallet, testTokenId),
        testSignature
      )
    } returns Eip712Verifier.VerificationResult.Success(testWallet, testTokenId)

    every { ethereumService.verifyNftOwnership(testWallet, testTokenId) } returns true
    every { accountRepository.findByNftTokenId(testTokenId) } returns Optional.empty()
    every { accountRepository.save(any<Account>()) } returns newAccount
    every { jwtService.createRefreshToken(newAccount.id, testWallet, testTokenId) } returns testRefreshToken

    // When
    val result = loginService.authenticate(request)

    // Then
    assertTrue(result is Eip712AuthenticationService.AuthResult.Success)
    val successResult = result as Eip712AuthenticationService.AuthResult.Success
    assertEquals(testJwtToken, successResult.jwtToken)
    assertEquals(newAccount.id, successResult.accountId)

    verify(exactly = 1) { accountRepository.findByNftTokenId(testTokenId) }
    verify(exactly = 2) { accountRepository.save(any<Account>()) } // Once for create, once for update lastLogin
    verify(exactly = 1) { jwtService.createRefreshToken(newAccount.id, testWallet, testTokenId) }
  }

  @Test
  fun `login should fail when signature verification fails`() {
    // Given
    val request = Eip712AuthenticationController.AuthRequest(testWallet, testTokenId, testSignature)
    val errorMessage = "Invalid signature"

    every {
      eip712Verifier.verifySignature(
        Eip712Verifier.LoginPayload(testWallet, testTokenId),
        testSignature
      )
    } returns Eip712Verifier.VerificationResult.Failure(errorMessage)

    // When
    val result = loginService.authenticate(request)

    // Then
    assertTrue(result is Eip712AuthenticationService.AuthResult.Failure)
    assertEquals(
      "Signature verification failed: $errorMessage",
      (result as Eip712AuthenticationService.AuthResult.Failure).error
    )

    verify(exactly = 1) {
      eip712Verifier.verifySignature(
        Eip712Verifier.LoginPayload(testWallet, testTokenId),
        testSignature
      )
    }
    verify(exactly = 0) { ethereumService.verifyNftOwnership(any(), any()) }
    verify(exactly = 0) { jwtService.createRefreshToken(any(), any(), any()) }
  }

  @Test
  fun `login should fail when NFT ownership verification fails`() {
    // Given
    val request = Eip712AuthenticationController.AuthRequest(testWallet, testTokenId, testSignature)

    every {
      eip712Verifier.verifySignature(
        Eip712Verifier.LoginPayload(testWallet, testTokenId),
        testSignature
      )
    } returns Eip712Verifier.VerificationResult.Success(testWallet, testTokenId)

    every { ethereumService.verifyNftOwnership(testWallet, testTokenId) } returns false

    // When
    val result = loginService.authenticate(request)

    // Then
    assertTrue(result is Eip712AuthenticationService.AuthResult.Failure)
    assertEquals(
      "NFT ownership verification failed",
      (result as Eip712AuthenticationService.AuthResult.Failure).error
    )

    verify(exactly = 1) {
      eip712Verifier.verifySignature(
        Eip712Verifier.LoginPayload(testWallet, testTokenId),
        testSignature
      )
    }
    verify(exactly = 1) { ethereumService.verifyNftOwnership(testWallet, testTokenId) }
    verify(exactly = 0) { jwtService.createRefreshToken(any(), any(), any()) }
  }

  @Test
  fun `login should fail when database save fails`() {
    // Given
    val request = Eip712AuthenticationController.AuthRequest(testWallet, testTokenId, testSignature)
    val testAccount = Account(testTokenId)

    every {
      eip712Verifier.verifySignature(
        Eip712Verifier.LoginPayload(testWallet, testTokenId),
        testSignature
      )
    } returns Eip712Verifier.VerificationResult.Success(testWallet, testTokenId)

    every { ethereumService.verifyNftOwnership(testWallet, testTokenId) } returns true
    every { accountRepository.findByNftTokenId(testTokenId) } returns Optional.of(testAccount)
    every { accountRepository.save(any<Account>()) } throws RuntimeException("Database connection failed")

    // When & Then
    assertThrows(RuntimeException::class.java) {
      loginService.authenticate(request)
    }

    verify(exactly = 1) { ethereumService.verifyNftOwnership(testWallet, testTokenId) }
    verify(exactly = 1) { accountRepository.findByNftTokenId(testTokenId) }
  }

  @Test
  fun `login should handle different signature verification error messages`() {
    // Given
    val request = Eip712AuthenticationController.AuthRequest(testWallet, testTokenId, testSignature)
    val customErrorMessage = "Signature recovery failed: Invalid signature format"

    every {
      eip712Verifier.verifySignature(
        Eip712Verifier.LoginPayload(testWallet, testTokenId),
        testSignature
      )
    } returns Eip712Verifier.VerificationResult.Failure(customErrorMessage)

    // When
    val result = loginService.authenticate(request)

    // Then
    assertTrue(result is Eip712AuthenticationService.AuthResult.Failure)
    assertEquals(
      "Signature verification failed: $customErrorMessage",
      (result as Eip712AuthenticationService.AuthResult.Failure).error
    )
  }

  @Test
  fun `login should work with different wallet addresses and token IDs`() {
    // Given
    val differentWallet = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef"
    val differentTokenId = 456L
    val request = Eip712AuthenticationController.AuthRequest(differentWallet, differentTokenId, testSignature)
    val differentAccount = Account(differentTokenId)

    every {
      eip712Verifier.verifySignature(
        Eip712Verifier.LoginPayload(differentWallet, differentTokenId),
        testSignature
      )
    } returns Eip712Verifier.VerificationResult.Success(differentWallet, differentTokenId)

    every { ethereumService.verifyNftOwnership(differentWallet, differentTokenId) } returns true
    every { accountRepository.findByNftTokenId(differentTokenId) } returns Optional.of(differentAccount)
    every { accountRepository.save(any<Account>()) } returns differentAccount
    every { jwtService.createRefreshToken(differentAccount.id, differentWallet, differentTokenId) } returns testRefreshToken

    // When
    val result = loginService.authenticate(request)

    // Then
    assertTrue(result is Eip712AuthenticationService.AuthResult.Success)
    assertEquals(testJwtToken, (result as Eip712AuthenticationService.AuthResult.Success).jwtToken)

    verify(exactly = 1) {
      eip712Verifier.verifySignature(
        Eip712Verifier.LoginPayload(differentWallet, differentTokenId),
        testSignature
      )
    }
    verify(exactly = 1) { ethereumService.verifyNftOwnership(differentWallet, differentTokenId) }
    verify(exactly = 1) { jwtService.createRefreshToken(differentAccount.id, differentWallet, differentTokenId) }
  }

  @Test
  fun `login should update lastLogin timestamp on existing account`() {
    // Given
    val request = Eip712AuthenticationController.AuthRequest(testWallet, testTokenId, testSignature)
    val existingAccount = Account(testTokenId)
    val updatedAccount = slot<Account>()

    every {
      eip712Verifier.verifySignature(
        Eip712Verifier.LoginPayload(testWallet, testTokenId),
        testSignature
      )
    } returns Eip712Verifier.VerificationResult.Success(testWallet, testTokenId)

    every { ethereumService.verifyNftOwnership(testWallet, testTokenId) } returns true
    every { accountRepository.findByNftTokenId(testTokenId) } returns Optional.of(existingAccount)
    every { accountRepository.save(capture(updatedAccount)) } returns existingAccount
    every { jwtService.createRefreshToken(existingAccount.id, testWallet, testTokenId) } returns testRefreshToken

    // When
    val result = loginService.authenticate(request)

    // Then
    assertTrue(result is Eip712AuthenticationService.AuthResult.Success)
    assertNotNull(updatedAccount.captured.lastLogin)
    verify(exactly = 1) { accountRepository.save(any<Account>()) }
  }
}
