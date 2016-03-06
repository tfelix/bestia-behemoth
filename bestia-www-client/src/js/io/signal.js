/**
 * Constants for inter app signalling. In order to prevent typos please only use
 * these defined constant signals when sending and subscribing to inter app
 * messaging via a Bestia.PubSub interface.
 */
Bestia.Signal = {

	LOGOUT : 'system.logout',
	
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

	IO_SEND_MESSAGE : 'io.sendMessage',
	IO_CONNECT : 'io.connect',
	IO_DISCONNECT : 'io.disconnect',

	IO_CONNECTING : 'io.connecting',
	IO_DISCONNECTED : 'io.disconnected',
	IO_CONNECTED : 'io.connected',

	CHAT_RECEIVED : 'client.chat.received',

	ENGINE_LOADED : 'engine.loaded',
	ENGINE_CONNECTED : 'engine.connected',
	ENGINE_DISCONNECTED : 'engine.disconnected',
	ENGINE_PREPARE_MAPLOAD : 'engine.prepareMapload',
	ENGINE_FINISHED_MAPLOAD : 'engine.finishMapload',

	ENGINE_CAST_ITEM : 'engine.castItem',

	/**
	 * Emitted when the game has loaded and is being displayed.
	 */
	ENGINE_GAME_STARTED : 'engine.gameStarted'

};