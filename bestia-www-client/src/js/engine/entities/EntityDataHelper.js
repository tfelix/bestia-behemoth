
/**
 * Contains function to help manipulate the entity data structures.
 */


/**
 * Adds a movement structure to an entity.
 * 
 * @param {object} entity - The entity to add the movement structure to.
 * @param {array} path - An array containing points (x and y objects) to describe a movement path.
 * @param {float} walkspeed - The walkspeed of the entity.
 * @param {delta} delta - The time delay for this movement and this client. The movement 
 * has started since this time already and must be speed up to compensate for this.
 */
function entityAddMovement(entity, path, walkspeed, delta) {

    walkspeed = walkspeed || 1;
    delta = delta || 0;

    entity.movement = {
        path: path,
        speed: walkspeed,
        delta: delta
    };

}

export { entityAddMovement };