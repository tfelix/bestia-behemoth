package net.bestia.zoneserver.entity.component;

public class PlayerComponent extends Component {

	private static final long serialVersionUID = 1L;
	private long ownerAccountId;
	private long playerBestiaId;

	public PlayerComponent(long id, long entityId) {
		super(id, entityId);
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

	@Override
	public String toString() {
		return String.format("PlayerComponent[id: %d, accId: %d, pbId: %d]", getId(), getOwnerAccountId(), getPlayerBestiaId());
	}

}
