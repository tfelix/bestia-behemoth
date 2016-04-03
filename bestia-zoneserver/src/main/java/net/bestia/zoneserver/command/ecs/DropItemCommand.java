package net.bestia.zoneserver.command.ecs;

import java.util.Random;

import net.bestia.messages.Message;
import net.bestia.messages.inventory.InventoryItemDropMessage;
import net.bestia.model.dao.ItemDAO;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.Location;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.entity.ItemEntityFactory;
import net.bestia.zoneserver.proxy.InventoryProxy;
import net.bestia.zoneserver.zone.shape.Vector2;

public class DropItemCommand extends ECSCommand {
	
	private final Random rand = new Random();
	private ItemEntityFactory itemCtrl;

	@Override
	public String handlesMessageId() {
		return InventoryItemDropMessage.MESSAGE_ID;
	}
	
	@Override
	protected void initialize() {
		super.initialize();
		
		 itemCtrl = new ItemEntityFactory(world);
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		final InventoryItemDropMessage msg = (InventoryItemDropMessage) message;

		// We need an inventory manager.
		final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
		final ItemDAO itemDao = ctx.getServiceLocator().getBean(ItemDAO.class);
		final InventoryProxy invManager = new InventoryProxy(getPlayerBestiaProxy(), invService, ctx.getServer());

		if (!invManager.removeItem(msg.getItemId(), msg.getAmount())) {
			// Either player did not own the item or not enough.
			return;
		}
		
		// Get position where to drop the item.
		final Location playerLoc = getPlayerBestiaProxy().getLocation();
		Vector2 loc = null;
		int maxTries = 10;
		while(maxTries-- > 0) {
			int x = rand.nextInt(3) - 1; // from -1 to 1.
			int y = rand.nextInt(3) - 1; // aswell from -1 to 1.
			
			// Dont drop on player spot.
			if(x == 0 && y == 0) {
				continue;
			}
			
			// Check if we dont drop onto a wall.
			Vector2 tempLoc = new Vector2(playerLoc.getX() - x, playerLoc.getY() - y);
			if(map.isWalkable(tempLoc)) {
				loc = tempLoc;
				break;
			}
		}
		
		if(loc == null) {
			// Just drop on player position
			loc = new Vector2(playerLoc.getX(), playerLoc.getY());
		}
		
		final Item item = itemDao.findOne(msg.getItemId());

		// Item was dropped. Now drop it onto the world.
		itemCtrl.spawnItem(loc, item, msg.getAmount());
	}

}
