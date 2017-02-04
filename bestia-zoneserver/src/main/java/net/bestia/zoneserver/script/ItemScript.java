package net.bestia.zoneserver.script;

import java.util.Objects;

import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.entity.LivingEntity;

public class ItemScript {

	private final static String KEY_PREFIX = "item.";

	private final String itemName;

	public ItemScript(String itemName) {
		this.itemName = Objects.requireNonNull(itemName);
	}

	public void onUse(long userAccId, LivingEntity user) {
		onUse(userAccId, user, null, null);
	}

	public void onUse(long userAccId, LivingEntity user, LivingEntity target) {
		onUse(userAccId, user, target, null);
	}

	public void onUse(long userAccId, LivingEntity user, LivingEntity target, Point targetPlace) {

	}

	@Override
	public String toString() {
		return String.format("ItemScript[%s]", itemName);
	}

	public String getScriptKey() {
		return String.format("%s%s", KEY_PREFIX, itemName);
	}

	/**
	 * Static version to create the script key. This is important for the script
	 * compiler.
	 * 
	 * @param itemName
	 *            The item name.
	 * @return The script key.
	 */
	public static String getScriptKey(String itemName) {
		return String.format("%s%s", KEY_PREFIX, itemName);
	}

}
