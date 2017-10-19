import Renderer from './Renderer';

/**
 * Shows the entity menu. Centered at the given entity id and 
 * with the menu options specified inside the data object.
 */
export function showEntityMenu(anchorEntityId, menuData) {

}

/**
 * Hides the entity menu again.
 */
export function hideEntityMenu() {

}

export class EntityMenuRenderer extends Renderer {

    constructor(game) {
        super();

        this._game = game;
    }

    get name() {
        return 'menu';
    }

    isDirty() {
        return false;
    }

    clear() {
        // no op
    }

    /**
     * Iterates through every entity and checks if there is render work to be done
     * to display stuff attached to this entity.
     */
    update() {
        
    }

}