package net.bestia.login.eip712

import net.bestia.login.account.Account
import net.bestia.login.account.AccountRepository
import net.bestia.login.ethereum.EthereumService
import net.bestia.login.jwt.JwtService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class Eip712AuthenticationService(
  private val eip712Verifier: Eip712Verifier,
  private val ethereumService: EthereumService,
  private val jwtService: JwtService,
  private val accountRepository: AccountRepository,
) {

  sealed class AuthResult {
    data class Success(
      val jwtToken: String,
      val accountId: Long
    ) : AuthResult()

    data class Failure(
      val error: String
    ) : AuthResult()
  }

  @Transactional
  fun authenticate(request: Eip712AuthenticationController.AuthRequest): AuthResult {
    // Step 1: Verify signature ownership
    val verificationResult = eip712Verifier.verifySignature(
      Eip712Verifier.LoginPayload(request.wallet, request.tokenIndex),
      request.signature
    )

    when (verificationResult) {
      is Eip712Verifier.VerificationResult.Failure -> {
        return AuthResult.Failure(error = "Signature verification failed: ${verificationResult.error}")
      }

      is Eip712Verifier.VerificationResult.Success -> {
        // Continue with the login process
      }
    }

    // Step 2: Verify NFT ownership
    if (!ethereumService.verifyNftOwnership(request.wallet, request.tokenIndex)) {
      return AuthResult.Failure(error = "NFT ownership verification failed")
    }

    // Step 3: Find or create account
    val account = findOrCreateAccount(request.tokenIndex)

    // Step 4: Update last login time
    updateLastLogin(account)

    // Step 5: Generate JWT token with account.id as subject
    val refreshToken = jwtService.createRefreshToken(
      account.id,
      request.wallet,
      request.tokenIndex
    )

    return AuthResult.Success(
      jwtToken = refreshToken.jwt,
      accountId = account.id
    )
  }

  private fun findOrCreateAccount(nftTokenId: Long): Account {
    return accountRepository.findByNftTokenId(nftTokenId)
      .orElseGet {
        val newAccount = Account(
          nftTokenId = nftTokenId
        )
        accountRepository.save(newAccount)
      }
  }

  private fun updateLastLogin(account: Account) {
    account.lastLogin = LocalDateTime.now()
    accountRepository.save(account)
  }
}