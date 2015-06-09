package net.bestia.loginserver.rest;

import java.util.Date;
import java.util.UUID;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.bestia.loginserver.authenticator.AuthState;
import net.bestia.loginserver.authenticator.PasswordAuthenticator;
import net.bestia.model.DAOLocator;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Password;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Path("v1/account")
public class AccountApi {

	private final static Logger log = LogManager.getLogger(AccountApi.class);
	private final static int FORBIDDEN_STATUS = 403;
	
	private final AccountDAO accountDao;
	
	public AccountApi() {
		DAOLocator daoLocator = new DAOLocator();
		this.accountDao = daoLocator.getObject(AccountDAO.class);
	}
	
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	@Path("/password")
	public Response password(@QueryParam("ident") String ident, @QueryParam("password") String password) {
		log.debug("Set new password: Ident: {}, Password: {}", ident, password);
		
		Account acc = accountDao.findByEmail(ident);
		
		if(acc == null) {
			return Response.status(404).build();
		}
		
		acc.setGold(acc.getGold() + 1);
		acc.setPassword(new Password(password));
		
		accountDao.save(acc);
		
		return Response.ok().build();
	}

	/***
	 * Used to perform a user login process. It will check the given user identifier (usually email address) and
	 * password and check it against in the database. If a match is found it will then proceed with logging in this
	 * user. A login token is generated and set in the database. The token is delivered to the user.
	 * If the user identifier or password does not match it will return a 403 FORBIDDEN status.
	 * 
	 * @param ident
	 * @param password
	 * @return
	 */
	@GET
	@Produces("text/plain; charset=UTF-8")
	@Path("/login")
	public Response login(@QueryParam("ident") String ident, @QueryParam("password") String password) {
		log.debug("Login request: Ident: {}, Password: {}", ident, password);
		
		PasswordAuthenticator auth = new PasswordAuthenticator(ident, password);
		
		if(auth.authenticate() == AuthState.AUTHENTICATED) {
			// Set new login token.
			final String token = UUID.randomUUID().toString();
			final Account acc = auth.getFoundAccount();
			
			acc.setLoginToken(token);
			acc.setLastLogin(new Date());
			
			accountDao.save(acc);
			
			// Return the login token.
			return Response.ok().entity(token).build();
		} else {
			return Response.status(FORBIDDEN_STATUS).build();
		}
	}

}
