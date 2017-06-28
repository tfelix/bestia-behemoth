package net.bestia.model.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

/**
 * This holds the shortcuts of items and attacks for a certain bestia.
 * 
 * @author Thomas Felix
 *
 */
@Entity
@Table(name = "clientvars", indexes = {
		@Index(columnList = "key", name = "key_idx")
})
public class ClientVar implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private long id;

	@OneToOne
	@JoinColumn(name = "ACCOUNT_ID", nullable = false)
	private Account account;

	/**
	 * Key for the given entry.
	 */
	private String key;

	/**
	 * Data containing this shortcut.
	 */
	private String data;

	public ClientVar() {
		// no op.
	}

	public ClientVar(Account acc, String key, String data) {

		this.account = Objects.requireNonNull(acc);
		this.key = Objects.requireNonNull(key);
		this.data = Objects.requireNonNull(data);
	}

	public long getId() {
		return id;
	}

	public String getKey() {
		return key;
	}

	public String getData() {
		return data;
	}

	public Account getAccount() {
		return account;
	}

	public void setData(String data) {
		this.data = Objects.requireNonNull(data);
	}

	@Override
	public String toString() {
		return String.format("Cvar[accId: %d, key: %s, data: %s]", account.getId(), key, data);
	}
}
