import MultispriteBuilder from './MultispriteBuilder.js';

/**
 * This will modify the multisprite entity so it matches an player entity.
 */
export default class PlayerMultispriteBuilder extends MultispriteBuilder {
	
	constructor(factory, ctx) {
		super(factory, ctx);
		
		// Register with factory.
		this.type = 'playermultisprite';
		this.version = 1;
	}
	
	build(data, desc) {
		if(data.onlyLoad) {
			return null;
		}
		
		var entity = super.build(data, desc);
		
		entity.playerBestiaId = data.pbid;
			
		return entity;
	}

	/**
	 * The type of the entities does now not match the sane check. It must be corrected.
	 */
	canBuild(data) {
		return data.t === 'PLAYER_ANIM';
	}

}