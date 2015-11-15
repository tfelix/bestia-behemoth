/**
 * Constants for inter app signalling. In order to prevent typos please only use
 * these defined constant signals when sending and subscribing to inter app
 * messaging via a Bestia.PubSub interface.
 */
Bestia.Signal = {
		
		BESTIA_SELECTED : 'client.bestia.selected',

		INVENTORY_NOTICE : 'inventory.notice',
		INVENTORY_ITEM_ADD : 'client.inventory.itemAdd',

		IO_SEND_MESSAGE : 'io.sendMessage',
		
		CHAT_RECEIVED : 'client.chat.received'
};