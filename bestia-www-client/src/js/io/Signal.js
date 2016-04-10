/**
 * Constants for inter app signalling. In order to prevent typos please only use
 * these defined constant signals when sending and subscribing to inter app
 * messaging via a Bestia.PubSub interface.
 */
var Signal = {

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
	
	/**
	 * Advises the chat to register an command.
	 */
	CHAT_REGISTER_CMD : 'c.chat.register_cmd',

	INVENTORY_NOTICE : 'inventory.notice',
	INVENTORY_ITEM_ADD : 'client.inventory.itemAdd',
	/**
	 * The slot number is given as data in the first argument.
	 */
	INVENTORY_CAST_SLOT: 'client.inventory.cast',
	
	/**
	 * Engine has decided where to cast the item.
	 */
	INVENTORY_PERFORM_CAST: 'client.inventory.performcast',

	IO_SEND_MESSAGE : 'io.sendMessage',
	IO_CONNECT : 'io.connect',
	IO_DISCONNECT : 'io.disconnect',

	IO_CONNECTING : 'io.connecting',
	IO_DISCONNECTED : 'io.disconnected',
	IO_CONNECTED : 'io.connected',

	CHAT_RECEIVED : 'client.chat.received',

	/**
	 * Engine is ready and can be used.
	 */
	ENGINE_BOOTED : 'engine.bootet',
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

export default Signal;