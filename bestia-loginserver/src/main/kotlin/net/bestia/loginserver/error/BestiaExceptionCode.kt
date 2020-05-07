package net.bestia.loginserver.error

enum class BestiaExceptionCode {
  // HTTP CODES
  BAD_REQUEST,

  AUTH_FAILED,
  AUTH_ACCOUNT_BANNED,

  ACCOUNT_REGISTER_USERNAME_IN_USE
}