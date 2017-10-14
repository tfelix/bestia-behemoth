package net.bestia.zoneserver.actor.rest;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import net.bestia.messages.account.AccountRegistration;
import net.bestia.zoneserver.service.AccountService;

/**
 * Performs an account registration procedure.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class RegisterAccountActor extends AbstractActor {

	private final AccountService accService;

	@Autowired
	public RegisterAccountActor(AccountService accService) {

		this.accService = Objects.requireNonNull(accService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(AccountRegistration.class, this::handleRegister)
				.build();
	}

	private void handleRegister(AccountRegistration data) {

		accService.createNewAccount(data);
		
	}
}
