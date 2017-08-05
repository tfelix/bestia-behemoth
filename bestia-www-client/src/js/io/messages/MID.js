/**
 * Constants with message IDs from the bestia server.
 * 
 * @constant
 */
var MID = {
	SERVER_INFO : 'server.info',

	SYSTEM_AUTH : 'system.loginauth',
	SYSTEM_AUTHREPLY : 'system.loginauthreply',

	LATENCY_REQ : 'lat.req',
	
	BESTIA_INFO : 'bestia.info',
	BESTIA_REQ_INFO : 'bestia.requestinfo',
	BESTIA_ACTIVATE: 'bestia.activate',
	
	ATTACK_SET : 'attack.set',
	ATTACK_LIST_REQUEST: 'attack.list.request',
	ATTACK_LIST_RESPONSE: 'attack.list.response',
	ATTACK_USE:	'attack.use',

	ANIMATION_PLAY: 'animation.play',
	
	ENTITY_DAMAGE: 'entity.damage',
	ENTITY_UPDATE: 'entity.update',
	ENTITY_POSITION: 'entity.position',
	ENTITY_MOVE: 'entity.move',
	ENTITY_STATUS: 'entity.status',
	
	MAP_TILESET: 'map.tileset',
	MAP_TILESET_REQUEST: 'map.tilesetrequest',
	MAP_CHUNK: 'map.chunk',
	MAP_CHUNK_REQUEST: 'map.requestdata',
	
	UI_DIALOG : 'ui.dialog',
	
	INVENTORY_UPDATE : 'inventory.update',
	INVENTORY_LIST: 'inventory.list',
	INVENTORY_LIST_REQUEST: 'inventory.requestlist',
	INVENTORY_ITEM_CAST: 'inventory.item.cast',
	INVENTORY_ITEM_CAST_CONFIRM: 'inventory.item.castconfirm',
	INVENTORY_ITEM_SET: 'inventory.item.set',
	INVENTORY_REQUEST: 'inventory.request',
	INVENTORY_ITEM_USE: 'inventory.item.use',
	INVENTORY_ITEM_DROP: 'inventory.item.drop',
	
	CHAT_MESSAGE: 'chat.message',
		
	TRANSLATION_REQ: 'translation.request',
	CVAR_REQ: 'cvar.req',
	CVAR: 'cvar'
};

export default MID;