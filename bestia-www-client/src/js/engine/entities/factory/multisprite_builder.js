/**
 * Responsible for building the multisprites entities.
 */
Bestia.Engine.MultispriteBuilder = function(factory) {
	Bestia.Engine.Builder.call(this, factory);

	// Register with factory.
	this.type = 'multisprite';
	this.version = 1;
};

Bestia.Engine.MultispriteBuilder.prototype = Object.create(Bestia.Engine.Builder.prototype);
Bestia.Engine.MultispriteBuilder.prototype.constructor = Bestia.Engine.MultispriteBuilder;

Bestia.Engine.MultispriteBuilder.prototype._build = function(data, desc) {

	var entity = new Bestia.Engine.MultispriteEntity(this._factory.game, data.uuid, desc);

	// Position
	entity.setPosition(data.x, data.y);

	entity.setSprite(data.s);

	if (data.a === "APPEAR") {
		entity.appear();
	} else {
		entity.show();
	}

	return entity;
};

/**
 * Responsible for loading all the necessairy date before a build of the object
 * can be performt.
 * 
 * @param data
 */
Bestia.Engine.MultispriteBuilder.prototype.load = function(descFile, fnOnComplete) {

	var pack = this._extendPack(descFile);
	this._factory.loader.loadPackData(pack, fnOnComplete);

};

Bestia.Engine.MultispriteBuilder.prototype._extendPack = function(descFile) {

	var pack = descFile.assetpack;
	var key = '';

	for ( var k in pack) {
		key = k;
		break;
	}

	var packArray = pack[key];

	var msprites = descFile.multiSprite;

	msprites.forEach(function(msName) {

		var entry = {
			type : "atlasJSONHash",
			key : msName,
			textureURL : "assets/sprite/multi/" + msName + "/" + msName + ".png",
			atlasURL : "assets/sprite/multi/" + msName + "/" + msName + ".json",
			atlasData : null
		};

		packArray.push(entry);

	}, this);

	return pack;
};
