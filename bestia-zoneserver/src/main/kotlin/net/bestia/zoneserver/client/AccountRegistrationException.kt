package net.bestia.zoneserver.client

import net.bestia.messages.account.AccountRegistrationError

class AccountRegistrationException(
        val reason: AccountRegistrationError
) : RuntimeException()