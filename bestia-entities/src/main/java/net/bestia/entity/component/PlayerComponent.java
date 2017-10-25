package net.bestia.entity.component;

import java.util.Objects;

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

	@Override
	public String toString() {
		return String.format("PlayerComponent[id: %d, accId: %d, pbId: %d]", getId(), getOwnerAccountId(),
				getPlayerBestiaId());
	}

	@Override
	public int hashCode() {
		return Objects.hash(ownerAccountId, playerBestiaId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof PlayerComponent)) {
			return false;
		}
		final PlayerComponent other = (PlayerComponent) obj;
		return Objects.equals(ownerAccountId, other.ownerAccountId)
				&& Objects.equals(playerBestiaId, other.playerBestiaId);
	}

}
