package net.bestia.messages.account;

import java.io.Serializable;
import java.util.Objects;

import net.bestia.model.server.MaintenanceLevel;

/**
 * Returns the current status of the server.
 * 
 * @author Thomas Felix
 *
 */
public final class ServerStatusMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	public final static class Request implements Serializable {
		private static final long serialVersionUID = 1L;
	}

	private MaintenanceLevel maintenance;
	private String motd;

	public ServerStatusMessage(MaintenanceLevel level, String motd) {

		this.maintenance = level;
		this.motd = Objects.requireNonNull(motd);
	}

	public MaintenanceLevel getMaintenance() {
		return maintenance;
	}

	public void setMaintenance(MaintenanceLevel maintenance) {
		this.maintenance = maintenance;
	}

	public String getMotd() {
		return motd;
	}

	public void setMotd(String motd) {
		this.motd = motd;
	}
}