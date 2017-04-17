package net.bestia.zoneserver.entity.components;

public class PlayerComponent extends Component {

	private static final long serialVersionUID = 1L;
	private long ownerAccountId;
	private long playerBestiaId;

	public PlayerComponent(long id) {
		super(id);
		// no op.
	}

	public long getOwnerAccountId() {
		return ownerAccountId;
	}

	public void setOwnerAccountId(long ownerAccountId) {
		if (ownerAccountId <= 0) {
			throw new IllegalArgumentException("ownerAccountId can not be null or negative.");
		}
		this.ownerAccountId = ownerAccountId;
	}

	public long getPlayerBestiaId() {
		return playerBestiaId;
	}

	public void setPlayerBestiaId(long playerBestiaId) {
		if (playerBestiaId <= 0) {
			throw new IllegalArgumentException("PlayerBestiaId can not be null or negative.");
		}
		this.playerBestiaId = playerBestiaId;
	}

}
