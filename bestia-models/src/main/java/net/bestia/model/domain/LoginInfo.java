package net.bestia.model.domain;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * This domain object is used to make a login history of the accounts.
 * 
 * TODO This must be implemented.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
public class LoginInfo {

	public enum LoginEvent {
		LOGIN, LOGOUT
	}

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private Account account;
	private Date eventDate;
	private LoginEvent eventType;
	private String ip;
	private String browserAgent;

	public LoginInfo() {

	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public Date getEventDate() {
		return eventDate;
	}

	public void setEventDate(Date eventDate) {
		this.eventDate = eventDate;
	}

	public LoginEvent getEventType() {
		return eventType;
	}

	public void setEventType(LoginEvent eventType) {
		this.eventType = eventType;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getBrowserAgent() {
		return browserAgent;
	}

	public void setBrowserAgent(String browserAgent) {
		this.browserAgent = browserAgent;
	}

	public Account getAccount() {
		return account;
	}

	public void setAccount(Account account) {
		this.account = account;
	}

	@Override
	public String toString() {
		return String.format("LoginInfo[accId: %d, %s, %s, %s]", getAccount().getId(), getEventDate(), getEventType(),
				getIp());
	}
}
