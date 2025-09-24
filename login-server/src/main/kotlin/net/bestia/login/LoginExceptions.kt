package net.bestia.login

abstract class LoginException(
  message: String,
  cause: Throwable? = null
) : Exception(message, cause)

class AccountNotFoundException(message: String = "Account not found") : LoginException(message)

class NftOwnershipVerificationFailedException(message: String = "NFT ownership verification failed") : LoginException(message)

class InternalLoginException(
  message: String,
  cause: Throwable? = null
) : LoginException("An error occurred during login: $message", cause)
