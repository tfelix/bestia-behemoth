package net.bestia.account

/** Why the login server ended an account's sessions. */
enum class KickReason {
  /** The owner recovered the account, so whoever else is signed in may hold a stolen passkey. */
  RECOVERED,

  /** The account may no longer log in. */
  BANNED,
}
