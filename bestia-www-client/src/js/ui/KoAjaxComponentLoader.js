import ko from 'knockout';
import LOG from '../util/Log';

/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

function callAjax(url, callback){
    var xmlhttp;
    // compatible with IE7+, Firefox, Chrome, Opera, Safari
    xmlhttp = new XMLHttpRequest();
    xmlhttp.onreadystatechange = function(){
        if (xmlhttp.readyState == 4 && xmlhttp.status == 200){
            callback(xmlhttp.responseText);
        }
    }
    xmlhttp.open('GET', url, true);
    xmlhttp.send();
}


/**
 * This is a custom component loader for knockout which is used to dynamically
 * fetch the templates for the components of this page like the chat or the
 * inventory.
 */
export default class KoAjaxComponentLoader {
	
    /**
     * Ctor. Creates a new component loader which is used to create the custom bestia 
     * components for knockout.
     */
	constructor() {
        //no op.
	}
	
	loadTemplate(name, templateConfig, callback) {
        if (templateConfig.fromUrl !== undefined) {
            // Uses jQuery's ajax facility to load the markup from a file
            var fullUrl = '/templates/' + templateConfig.fromUrl;

            LOG.debug('Fetching UI template:', fullUrl);
            
            callAjax(fullUrl, function(markupString) {
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

