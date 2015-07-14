package net.bestia.loginserver.rest;

import java.util.Date;
import java.util.UUID;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.bestia.loginserver.authenticator.AuthState;
import net.bestia.loginserver.authenticator.PasswordAuthenticator;
import net.bestia.loginserver.rest.response.AccountLoginResponse;
import net.bestia.messages.api.AccountCheckJson;
import net.bestia.model.ServiceLocator;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Password;
import net.bestia.model.service.AccountService;
import net.bestia.model.service.AccountService.Master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path("v1/account")
public class AccountApi {

	private final static Logger log = LogManager.getLogger(AccountApi.class);
	private final static int FORBIDDEN_STATUS = 403;

	private static final ObjectMapper mapper = new ObjectMapper();

	private final AccountDAO accountDao;
	private final AccountService accountService;

	public AccountApi() {
		ServiceLocator daoLocator = new ServiceLocator();
		this.accountDao = daoLocator.getBean(AccountDAO.class);
		this.accountService = daoLocator.getBean(AccountService.class);
	}

	@GET
	@Produces(MediaType.TEXT_PLAIN)
	@Path("/password")
	public Response password(@QueryParam("ident") String ident, @QueryParam("password") String password) {
		log.info("Set new password: Ident: {}, Password: {}", ident, password);

		Account acc = accountDao.findByEmail(ident);

		if (acc == null) {
			return Response.status(404).build();
		}

		acc.setGold(acc.getGold() + 1);
		acc.setPassword(new Password(password));

		accountDao.save(acc);

		return Response.ok().build();
	}

	/**
	 * Creates an new user account with the given username (email) and password.
	 * 
	 * @param email
	 *            Email of the user.
	 * @param password
	 *            Password.
	 * @param masterId
	 *            Select which master to use.
	 * @param username
	 *            Name of the master.
	 * @return
	 */
	@GET
	@Produces("application/json")
	@Path("/create/validate")
	public Response create(@QueryParam("email") String email, @QueryParam("username") String username) {

		// Check if there is already an account.
		AccountCheckJson answer = new AccountCheckJson();

		// Check email.
		if (accountDao.findByEmail(email) != null) {
			answer.emailUsed = true;
		}

		// Check username.
		if (accountDao.findByNickname(username) != null) {
			answer.nameUsed = true;
		}

		String answerString = "";
		// final String answerString = mapper.writeValueAsString(answer);
		return Response.ok().entity(answerString).build();
	}

	/**
	 * Creates an new user account with the given username (email) and password.
	 * 
	 * @param email
	 *            Email of the user.
	 * @param password
	 *            Password.
	 * @param masterId
	 *            Select which master to use.
	 * @param username
	 *            Name of the master.
	 * @return
	 */
	// TODO Der eingebaute Webserver unterstützt kein PUT und DELETE später austauschen und hier API abhändern.
	@GET
	@Produces("application/json")
	@Path("/create")
	public Response create(@QueryParam("email") String email, @QueryParam("password") String password,
			@QueryParam("username") String username, @DefaultValue("1") @QueryParam("master") int masterId) {

		// Falidate inputs.
		if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
			return Response.serverError().build();
		}

		// Switch master id.
		AccountService.Master master;
		switch (masterId) {
		case 1:
		case 2:
		case 3:
			master = Master.FIGHTER;
			break;
		default:
			master = Master.FIGHTER;
			break;
		}

		accountService.createNewAccount(email, username, password, master);

		log.info("Create new account: email: {}, username: {}, master_id: {}", email, username, masterId);

		return Response.ok().build();
	}

	/***
	 * Used to perform a user login process. It will check the given user identifier (usually email address) and
	 * password and check it against in the database. If a match is found it will then proceed with logging in this
	 * user. A login token is generated and set in the database. The token is delivered to the user. If the user
	 * identifier or password does not match it will return a 403 FORBIDDEN status.
	 * 
	 * @param ident
	 * @param password
	 * @return
	 */
	@GET
	@Produces("application/json")
	@Path("/login")
	public Response login(@QueryParam("ident") String ident, @QueryParam("password") String password) {
		log.debug("Login request: Ident: {}, Password: {}", ident, password);

		PasswordAuthenticator auth = new PasswordAuthenticator(ident, password);

		if (auth.authenticate() == AuthState.AUTHENTICATED) {
			// Set new login token.
			final String token = UUID.randomUUID().toString();
			final Account acc = auth.getFoundAccount();

			acc.setLoginToken(token);
			acc.setLastLogin(new Date());

			accountDao.save(acc);

			AccountLoginResponse loginResponse = new AccountLoginResponse(acc.getId(), acc.getName(), token);

			try {
				final String answer = mapper.writeValueAsString(loginResponse);
				// Return the login token.
				return Response.ok().entity(answer).build();
			} catch (JsonProcessingException e) {
				log.error("Could not generate JSON response.", e);
				return Response.serverError().entity("Could not process request. Try again later.").build();
			}
		} else {
			return Response.status(FORBIDDEN_STATUS).entity("Wrong login data.").build();
		}
	}

}
