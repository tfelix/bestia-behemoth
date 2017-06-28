/**
 * Constants for inter app signalling. In order to prevent typos please only use
 * these defined constant signals when sending and subscribing to inter app
 * messaging via a Bestia.PubSub interface.
 */
let Signal = {

	/**
	 * Signal to logout the user.
	 */
	SYSTEM_LOGOUT: 'system.logout',
	I18N_LANG_CHANGED: 'i18n.lang',

	BESTIA_SELECTED: 'client.bestia.selected',

	INVENTORY_NOTICE: 'inventory.notice',
	INVENTORY_ITEM_ADD: 'client.inventory.itemAdd',
	/**
	 * The slot number is given as data in the first argument.
	 */
	INVENTORY_CAST_SLOT: 'client.inventory.cast',

	/**
	 * Engine has decided where to cast the item.
	 */
	INVENTORY_PERFORM_CAST: 'client.inventory.performcast',

	/**
	 * Sends a cvar request to the cvar manager.
	 */
	CVAR: 'client.cvarreq',

	// ### CONNECTION SIGNALS

	IO_SEND_MESSAGE: 'io.sendMessage',
	IO_CONNECT: 'io.connect',
	IO_DISCONNECT: 'io.disconnect',
	IO_CONNECTING: 'io.connecting',
	IO_DISCONNECTED: 'io.disconnected',
	IO_CONNECTED: 'io.connected',
	IO_AUTH_CONNECTED: 'io.authconnected',

	/**
	 * Emitted when the server does not authenticate or auth data is not
	 * complete.
	 */
	IO_AUTH_ERROR: 'io.autherror',

	CHAT_RECEIVED: 'client.chat.received',
	/**
	 * Advises the chat to register a new command.
	 */
	CHAT_REGISTER_CMD: 'chat.register_cmd',

	/**
	 * Controls if an input controller will listen for inputs or ignor them for
	 * a while.
	 */
	INPUT_LISTEN: 'input.listen',
	INPUT_USE_ATTACK: 'input.attack',
	INPUT_USE_ITEM: 'input.item',

	// ### SHORTCUTS
	SHORTCUT_REQ_SLOTS: 'shortcuts.slot.req',

	// ### ENGINE
	/**
	 * Returns the reference to the searched component or null if there is no such compoent.
	 */
	ENGINE_GET_ENTITY: 'engine.entity.get',

	/**
	 * Engine is ready and can be used.
	 */
	ENGINE_BOOTED: 'engine.booted',
	ENGINE_INIT_LOADED: 'engine.initloaded',
	ENGINE_LOADED: 'engine.loaded',
	ENGINE_PREPARE_MAPLOAD: 'engine.prepareMapload',
	/**
	 * Emitted when the map and the game state has loaded and is being
	 * displayed.
	 */
	ENGINE_GAME_STARTED: 'engine.gameStarted',

	/**
	 * Emitted when map data has loaded and we can now switch to the game state.
	 */
	ENGINE_FINISHED_MAPLOAD: 'engine.finishMapload',

	ENGINE_CAST_ITEM: 'engine.castItem',
	ENGINE_REQUEST_INDICATOR: 'engine.indicator',

	/**
	 * Engine debug command send via the chat. This is transported to the engine to be executed.
	 */
	ENGINE_DEBUG_CMD: 'engine.dbgcmd',
};

export default Signal;