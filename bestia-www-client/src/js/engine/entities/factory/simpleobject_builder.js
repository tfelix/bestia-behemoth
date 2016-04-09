/**
 * Responsible for building simple objects.
 */
Bestia.Engine.SimpleObjectBuilder = function(factory, game) {

	// Register with factory.
	this.type = 'simpleobject';
	this.version = 1;

	this._game = game;
	this._data = null;
};

Bestia.Engine.SimpleObjectBuilder.prototype = Object.create(Bestia.Engine.Builder.prototype);
Bestia.Engine.SimpleObjectBuilder.prototype.constructor = Bestia.Engine.SimpleObjectBuilder;

Bestia.Engine.SimpleObjectBuilder.prototype._build = function(data) {

	this._data = data;

	for (var i = 0; i < data.template.length; i++) {
		//var t = data.template[i];

	}

};

Bestia.Engine.SimpleObjectBuilder.prototype._getType = function(template) {
	// Check the type of the given template.
	for (var i = 0; i < this._data.data.length; i++) {
		if (this._data.data[i].name === template.name) {
			return this._data.data[i].type;
		}
	}

	return "unknown";
};

Bestia.Engine.SimpleObjectBuilder.prototype._createNode = function(template) {
	// Check the type of the given template.
	var type = this._getType(template);

	switch (type) {
	case 'particle':

		break;
	case 'sprite':
		return this._game.add.sprite(-100, -100, template.name);
		break;
	case 'filter':
		console.warn("Not yet supported");
		break;
	default:
		// no supported.
		return null;
	}
};

