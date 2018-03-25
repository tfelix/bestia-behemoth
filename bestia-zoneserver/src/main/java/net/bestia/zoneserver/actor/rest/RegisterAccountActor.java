package net.bestia.zoneserver.actor.rest;

import akka.actor.AbstractActor;
import net.bestia.messages.account.AccountRegistration;
import net.bestia.zoneserver.client.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Performs an account registration procedure.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class RegisterAccountActor extends AbstractActor {
	
	public final static String NAME = "RESTregisterAccount";

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
