/**
 * Constants for inter app signalling. In order to prevent typos please only use
 * these defined constant signals when sending and subscribing to inter app
 * messaging via a Bestia.PubSub interface.
 */
Bestia.Signal = {

	/**
	 * Emitted when the system tries to authenticate.
	 */
	AUTH : 'system.auth',

	/**
	 * Emitted when the server does not authenticate or auth data is not
	 * complete.
	 */
	AUTH_ERROR : 'system.autherror',

	BESTIA_SELECTED : 'client.bestia.selected',

	INVENTORY_NOTICE : 'inventory.notice',
	INVENTORY_ITEM_ADD : 'client.inventory.itemAdd',
	INVENTORY_CAST_SLOT_1: 'client.inventory.cast.1',
	INVENTORY_CAST_SLOT_2: 'client.inventory.cast.2',
	INVENTORY_CAST_SLOT_3: 'client.inventory.cast.3',
	INVENTORY_CAST_SLOT_4: 'client.inventory.cast.4',
	INVENTORY_CAST_SLOT_5: 'client.inventory.cast.5',
	INVENTORY_CONFIRM_CAST: 'client.inventory.confirmcast',

	IO_SEND_MESSAGE : 'io.sendMessage',
	IO_CONNECT : 'io.connect',
	IO_DISCONNECT : 'io.disconnect',

	IO_CONNECTING : 'io.connecting',
	IO_DISCONNECTED : 'io.disconnected',
	IO_CONNECTED : 'io.connected',

	CHAT_RECEIVED : 'client.chat.received',

	ENGINE_LOADED : 'engine.loaded',
	ENGINE_PREPARE_MAPLOAD : 'engine.prepareMapload',

	/**
	 * Emitted when map data has loaded and we can now switch to the game state.
	 */
	ENGINE_FINISHED_MAPLOAD : 'engine.finishMapload',

	ENGINE_CAST_ITEM : 'engine.castItem',
	
	

	/**
	 * Emitted when the map and the game state has loaded and is being
	 * displayed.
	 */
	ENGINE_GAME_STARTED : 'engine.gameStarted'

};