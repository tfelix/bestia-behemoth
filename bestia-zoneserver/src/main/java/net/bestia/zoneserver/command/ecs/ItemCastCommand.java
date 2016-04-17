package net.bestia.zoneserver.command.ecs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.annotations.Wire;

import net.bestia.messages.Message;
import net.bestia.messages.inventory.InventoryItemCastConfirmMessage;
import net.bestia.messages.inventory.InventoryItemCastMessage;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.ItemType;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.messaging.AccountRegistry;
import net.bestia.zoneserver.proxy.InventoryProxy;
import net.bestia.zoneserver.proxy.PlayerBestiaEntityProxy;
import net.bestia.zoneserver.script.Script;
import net.bestia.zoneserver.script.ScriptApi;
import net.bestia.zoneserver.script.ScriptBuilder;

/**
 * This will handle all messages for casting an item onto the map. It will then
 * send confirmation to the client if the cast has been successful.
 * 
 * Kann mit Item_Use zusammengelegt werden.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class ItemCastCommand extends ECSCommand {

	private final static Logger LOG = LogManager.getLogger(ItemCastCommand.class);

	@Wire
	private PlayerBestiaSpawnManager playerBestiaManager;

	@Wire
	private ScriptApi mapScriptApi;

	@Override
	protected void initialize() {
		super.initialize();

		world.inject(this);
	}

	@Override
	public String handlesMessageId() {
		return InventoryItemCastMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		final InventoryItemCastMessage castMsg = (InventoryItemCastMessage) message;

		final AccountRegistry register = ctx.getAccountRegistry();
		final long accId = castMsg.getAccountId();

		final int activeBestiaId = register.getActiveBestia(accId);
		final PlayerBestiaEntityProxy owner = playerBestiaManager.getPlayerBestiaProxy(activeBestiaId);

		final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
		final InventoryProxy inventory = new InventoryProxy(owner, invService, ctx.getServer());

		// Has the use the item in inventory? No? Abort and notify the user.
		if (!inventory.hasPlayerItem(castMsg.getPlayerItemId(), 1)) {
			final InventoryItemCastConfirmMessage confirmMsg = new InventoryItemCastConfirmMessage(castMsg, false);
			ctx.getServer().sendMessage(confirmMsg);
			return;
		}

		final Item item = inventory.getPlayerItem(castMsg.getPlayerItemId()).getItem();

		if (item.getType() != ItemType.CASTABLE && item.getType() != ItemType.USABLE) {
			// Item can not be used.
			LOG.trace("Item not castable.");
			final InventoryItemCastConfirmMessage confirmMsg = new InventoryItemCastConfirmMessage(castMsg, false);
			ctx.getServer().sendMessage(confirmMsg);
			return;
		}

		if (item.getType() == ItemType.CASTABLE && !isInRange(item, castMsg.getX(), castMsg.getY(), owner)) {
			// Target not in range.
			LOG.trace("Item not in castrange.");
			final InventoryItemCastConfirmMessage confirmMsg = new InventoryItemCastConfirmMessage(castMsg, false);
			ctx.getServer().sendMessage(confirmMsg);
			return;
		}

		// Prepare the script.
		final ScriptBuilder scriptBuilder = new ScriptBuilder();
		scriptBuilder.setApi(mapScriptApi)
				.setName(item.getItemDbName())
				.setInventory(inventory)
				.setOwnerEntity(getPlayerBestiaProxy())
				.setTargetCoordinates(castMsg.getX(), castMsg.getY())
				.setTargetEntity(null)
				.setScriptPrefix(Script.SCRIPT_PREFIX_ITEM);

		// Use the item.
		final Script script = scriptBuilder.build();
		final boolean success = ctx.getScriptManager().execute(script);

		if (success) {
			inventory.removeItem(item.getId(), 1);
			final InventoryItemCastConfirmMessage confirmMsg = new InventoryItemCastConfirmMessage(castMsg, true);
			ctx.getServer().sendMessage(confirmMsg);
			LOG.info("Cast/Use item: {}, accId: {}, x: {}, y: {}", item, accId, castMsg.getX(), castMsg.getY());
		} else {
			final InventoryItemCastConfirmMessage confirmMsg = new InventoryItemCastConfirmMessage(castMsg, false);
			ctx.getServer().sendMessage(confirmMsg);
			LOG.info("Could not cast/use item: {}, accId: {},  x: {}, y: {}", item, accId, castMsg.getX(),
					castMsg.getY());
		}
	}

	@Override
	public String toString() {
		return "ItemCastCommand[]";
	}

	private boolean isInRange(Item item, int targetX, int targetY, PlayerBestiaEntityProxy caster) {
		final int range = item.getUsableRange();

		final int x = caster.getLocation().getX();
		final int y = caster.getLocation().getY();

		final int dX = targetX - x;
		final int dY = targetY - y;

		return Math.sqrt(dX * dX + dY * dY) < range;
	}
}
