package net.bestia.zoneserver.ecs.entity;

public class ItemEntityBuilder extends EntityBuilder {
	
	int amount = 1;
	int itemId;
	int playerItemId;

	public ItemEntityBuilder() {
		// Force Item Type.
		super.setEntityType(EntityType.ITEM);
	}

	@Override
	public EntityBuilder setEntityType(EntityType type) {
		// no op.
		return this;
	}
	
	public void setItemID(int itemId) {
		this.itemId = itemId;
	}
	
	public void setPlayerItemID(int playerItemId) {
		this.playerItemId = playerItemId;
	}

	public void setAmount(int amount) {
		this.amount = amount;
	}

}
