package net.bestia.login

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.login.account.AccountRepository
import net.bestia.login.ethereum.EthereumService
import net.bestia.login.jwt.JwtService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class LoginService(
  private val jwtService: JwtService,
  private val accountRepository: AccountRepository,
  private val ethereumService: EthereumService
) {

  fun login(refreshToken: String): String {
    try {
      // Get and validate the JWT refresh token
      val refreshClaims = jwtService.validateRefreshToken(refreshToken)

      // Find the account
      val account = accountRepository.findByIdOrNull(refreshClaims.accountId)
        ?: throw AccountNotFoundException()

      // Verify ownership of NFT token
      if (!ethereumService.verifyNftOwnership(refreshClaims.wallet, refreshClaims.tokenId)) {
        throw NftOwnershipVerificationFailedException()
      }

      // Get permissions for the account
      val permissions = account.permissions.map { it.authority }

      // Update last login time
      account.lastLogin = LocalDateTime.now()
      accountRepository.save(account)

      // Create and return JWT login token
      return jwtService.createLoginToken(refreshClaims.accountId, permissions)
    } catch (e: LoginException) {
      LOG.warn(e) { "Login attempt failed: ${e.message}" }

      throw e // Re-throw login exceptions
    } catch (e: Exception) {
      LOG.warn(e) { "Login attempt failed: ${e.message}" }

      throw InternalLoginException(e.message ?: "Unknown error")
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
