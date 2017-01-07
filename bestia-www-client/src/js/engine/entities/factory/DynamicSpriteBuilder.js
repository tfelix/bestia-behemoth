import PackSpriteBuilder from './PackSpriteBuilder.js';

/**
 * This is able to create sprite entities which differ to the runtime. It must
 * react automatically when created to data described inside its description
 * file.
 */
export default class DynamicSpriteBuilder extends PackSpriteBuilder {
	
	constructor(factory, ctx) {
		super(factory, ctx);
		
		// Register with factory.
		this.type = 'dynamic';
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
	 * The type of the entities does now not match the sane check. It must be
	 * corrected.
	 */
	canBuild(data) {
		return data.t === 'DYNAMIC';
	}

}