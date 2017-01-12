
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
	
	constructor() {
		// no op.
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

ko.components.loaders.unshift(new KoAjaxComponentLoader());

