
/**
 * Message advises the server to send back a complete list with entities in update
 * range for the currently selected requesting player entity.
 */
export default class EntitySyncRequestMessage {

	constructor() {
		this.mid = EntitySyncRequestMessage.MID;
	}

}

EntitySyncRequestMessage.MID = 'entity.sync';