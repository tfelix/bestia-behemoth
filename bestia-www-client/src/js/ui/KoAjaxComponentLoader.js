import Chat from '../chat/Chat';

/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

/**
 * This is a custom component loader for knockout which is used to dynamically
 * fetch the templates for the components of this page like the chat or the
 * inventory.
 */
export default class KoAjaxComponentLoader {
	
	constructor(pubsub) {
		if(!pubsub) {
			throw 'Pubsub can not be empty.';
		}
		
		this._pubsub = pubsub;
	}
	
	/**
	 * Defines a viewmodel factory and dependency inject the pubsub object for
	 * communication.
	 */
	loadViewModel(name, viewModelConfig, callback) {
		// You could use arbitrary logic, e.g., a third-party
        // code loader, to asynchronously supply the constructor.
        // For this example, just use a hard-coded constructor function.
		var that = this;
		var viewModelConstructor = function(params, componentInfo){
			return new Chat(that._pubsub);
		};

        // We need a createViewModel function, not a plain constructor.
		// callback will transform it for us.
		callback(viewModelConstructor);
	}
	
	loadTemplate(name, templateConfig, callback) {
        if (templateConfig.fromUrl) {
            // Uses jQuery's ajax facility to load the markup from a file
            var fullUrl = '/templates/' + templateConfig.fromUrl;
            
            $.get(fullUrl, function(markupString) {
                // We need an array of DOM nodes, not a string.
                // We can use the default loader to convert to the
                // required format.
                ko.components.defaultLoader.loadTemplate(name, markupString, callback);
            });
            
        } else {
            // Unrecognized config format. Let another loader handle it.
            callback(null);
        }
    }
	
}

