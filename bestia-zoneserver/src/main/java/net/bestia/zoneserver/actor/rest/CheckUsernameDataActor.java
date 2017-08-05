package net.bestia.zoneserver.actor.rest;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.web.UserNameCheck;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.zone.IngestExActor;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectRequestMessage;

/**
 * Checks if a username and email is available.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class CheckUsernameDataActor extends AbstractActor {

	public final static String NAME = "RESTcheckUsername";
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private final AccountDAO accDao;

	@Autowired
	public CheckUsernameDataActor(AccountDAO accDao) {

		this.accDao = Objects.requireNonNull(accDao);
	}

	@Override
	public Receive createReceive() {

		final RedirectRequestMessage req = RedirectRequestMessage.get(UserNameCheck.class);
		getContext().actorSelection(AkkaCluster.getNodeName(IngestExActor.NAME)).tell(req, getSelf());

		return receiveBuilder()
				.match(UserNameCheck.class, this::handleUserNameCheck)
				.build();
	}

	private void handleUserNameCheck(UserNameCheck data) {
		
		LOG.debug("Check data: {}", data);

		Account acc = accDao.findByEmail(data.getEmail());
		
		if(acc == null) {
			data.setEmailAvailable(true);
		} else {
			data.setEmailAvailable(false);
		}
		
		acc = accDao.findByUsername(data.getUsername());
		
		if(acc == null) {
			data.setUsernameAvailable(true);
		} else {
			data.setUsernameAvailable(false);
		}
		
		// Reply the message.
		getSender().tell(data, getSelf());
	}
}
