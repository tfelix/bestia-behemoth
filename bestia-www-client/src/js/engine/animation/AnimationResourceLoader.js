import LOG from '../../util/Log';

/**
 * The loader will perform a load of all needed resources of an animation. 
 * The special thing is that animation data can be chained. So downloads might 
 * be chained.
 */
export default class AnimationResourceLoader {

    /**
     * Ctor. Creates a loader specific for animation resources.
     * 
     * @param {DemandLoader} loader Loader helper for downloading assets.
     * @param {UrlHelper} urlHelper Helper to generate urls.
     */
    constructor(loader, urlHelper) {
        if (!loader) {
            throw 'Loader is not defined.';
        }
        if (!urlHelper) {
            throw 'urlHelper is not defined.';
        }

        this._url = urlHelper;
        this._loader = loader;

        this._loadedResources = {};
    }

    /**
     * Transforms the animation assets into a format usable by the DemandLoader.
     * 
     * @param {Object} resource Object to transform.
     * @return {Object} A object usable by the DemandLoader.
     */
    _transformLoaderResource(resource) {
        let obj = {
            key: resource.res_id,
            type: resource.type,
            url: null
        };

        switch (obj.type) {
            case 'sound':
                obj.url = this._url.getSoundUrl(resource.category, resource.name);
                break;
            case 'image':
                obj.url = this._url.getSprite(resource.category, resource.name);
                break;
            default:
                LOG.error('Unknown resource type: ' + obj.type);
                return null;
        }

        return obj;
    }

    /**
     * Loads all resources (recursivly) until all is resolved. Then the callback function is invoked.
     * 
     * @param {Object[]} resources  A resource object describing all needed resources to use a given animation.
     * @param {Function} fn Callback function after all ressources have been loaded.
     */
    load(resources, fn) {

        // Scan if we have loaded all assets.
        let notLoaded = [];
        for (let i = 0; i < resources.length; i++) {
            if (this._loadedResources.hasOwnProperty(resources[i].res_id)) {
                notLoaded.push(this._transformLoaderResource(resources[i]));
            }
        }

        if (notLoaded.length === 0) {
            // All has loaded.
            fn();
        }

        // Build our loading context.
        let ctx = { res: notLoaded, callback: fn };     
        // Copy so we dont remove it while we iterate.
        let resCopy = ctx.res.slice(0);

        for(let i = 0; i < ctx.res.length; i++) {
            this._load.load(ctx.res[i], function(){

                // Remove the file from cache if loaded.
                resCopy = resCopy.splice(i, 1);
                if(resCopy.length === 0) {
                    ctx.fn();
                }
            });
        }
    }

    /**
     * Clears all loaded and cached ids basically resetting the loader.
     */
    clear() {

    }
}