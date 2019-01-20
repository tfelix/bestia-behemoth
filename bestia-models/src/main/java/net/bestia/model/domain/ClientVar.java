package net.bestia.model.domain;

import net.bestia.model.account.Account;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Objects;

import javax.persistence.Column;
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
		@Index(columnList = "cvar_key", name = "key_idx")
})
public class ClientVar implements Serializable {

	private static final Charset UTF_8 = Charset.forName("UTF-8");

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
	@Column(name = "cvar_key")
	private String key;

	/**
	 * Data containing this shortcut.
	 */
	private byte[] data;

	public ClientVar() {
		// no op.
	}

	public ClientVar(Account acc, String key, String data) {

		this.account = Objects.requireNonNull(acc);
		this.key = Objects.requireNonNull(key);
		setData(data);
	}

	public long getId() {
		return id;
	}

	public String getKey() {
		return key;
	}

	public int getDataLength() {
		return (data == null) ? 0 : data.length;
	}

	public String getData() {
		return (data == null) ? "" : new String(data, UTF_8);
	}

	public Account getAccount() {
		return account;
	}

	public void setData(String data) {
		this.data = data.getBytes(UTF_8);
	}

	@Override
	public String toString() {
		return String.format("Cvar[accId: %d, key: %s, data: %s]", account.getId(), key, data);
	}
}
