Bestia.Engine.FX = Bestia.Engine.FX || {};

/**
 * The dialog manager will listen for dialog messages from the server and
 * display them to the user. It is also responsible for sending back the
 * response to the server if the user is able to interact with this dialog. For
 * some dialogs it might be needed to access the game and find the entity to
 * which it belongs in order to position it correctly.
 * 
 * Sample JSON: [{type: name, text: 'john'}, {type: 'text', text: 'lalalalalalall'}]
 * 
 * @class Bestia.Engine.FX.Dialog
 */
Bestia.Engine.FX.Dialog = function(ctx, parentEle) {

	this._game = ctx.game;

	this._pubsub = ctx.pubsub;

	this._domEle = $('<div id="ui-dialog"></div>');
	this._domEle.hide().addClass('ui-dialog');

	$(parentEle).append(this._domEle);

	ctx.pubsub.subscribe(Bestia.MID.UI_DIALOG, this.handleMessage.bind(this));
};

/**
 * Handles the incoming dialog message.
 * 
 * @private
 * @param _
 * @param msg
 */
Bestia.Engine.FX.Dialog.prototype.handleMessage = function(_, msg) {
	
	this._domEle.update();
	
	msg.forEach(function(val){
		var ele = this._createDOMElement(val);
		this._domEle.append(ele);
	}, this);
	
	this._domEle.show();
};

/**
 * It transforms one element of the message into an DOM element.
 * 
 * @private
 * @oaram obj - Data object describing the display.
 */
Bestia.Engine.FX.Dialog.prototype._createDOMElement = function(obj) {

	var ele = null;

	switch (obj.type) {
	case 'name':
		ele = $('<div></div>');
		ele.update(obj.text);
		break;
	case 'text':
		ele = $('<p></p>');
		ele.update(obj.text);
	break;
	default:
		// no op.
		console.warn("Unknown message type: " + JSON.stringify(obj));
		break;
	}

	return ele;
};