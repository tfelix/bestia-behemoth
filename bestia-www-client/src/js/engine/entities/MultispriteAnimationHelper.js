import LOG from '../../util/Log';

export function isMultisprite(sprite) {
    return sprite.bType === 'multi';
}

/**
 * Returns the current offset for the given subsprite, animation of the main
 * sprite and current animation frame.
 * 
 * @param {string} subsprite
 *            Name of the subsprite to look for its anchor offset.
 * @param {string} currentAnim
 *            Currently running animation of the main sprite.
 * @param {number} currentFrame
 *            The current frame of the main sprite. Note that frame numbers start with 1.
 * @returns
 */
function getSubspriteOffset(subsprite, currentAnim, currentFrame) {

    let subData = this._getSubspriteData(subsprite);

    for (let i = 0; i < subData.offsets.length; i++) {
        if (subData.offsets[i].triggered !== currentAnim) {
            continue;
        }

        if (subData.offsets[i].offsets.length > currentFrame - 1) {
            return subData.offsets[i].offsets[currentFrame - 1];
        } else {
            LOG.warn('getSubspriteOffset: Not enough frames found for:', subsprite, ' currentAnim:', currentAnim);
            return subData.defaultCords;
        }
    }

    // If nothing found return default.
    if (!subData.defaultCords) {
        LOG.warn('getSubspriteOffset: No default cords found for:', subsprite, 'currentAnim:', currentAnim);
        return NULL_OFFSET;
    }

    return subData.defaultCords;
}

/**
* Returns the name of the subsprite animation depending of the current
* 'main' animation running on the main sprite. Can be used to set the
* subsprite animations after the animation of the main sprite has changed.
* 
* @param {string}
*            subspriteName Name of the current subsprite.
* @param currentAnim
* @returns Name of the subsprite animation.
*/
function getSubspriteAnimation(subspriteName, currentAnim) {

    let subsprite = this._getSubspriteData(subspriteName);

    if (subsprite === null) {
        return null;
    }

    for (var i = 0; i < subsprite.offsets.length; i++) {
        if (subsprite.offsets[i].triggered === currentAnim) {
            return subsprite.offsets[i].name;
        }
    }

    // No anim found.
    return null;
}

/**
* Depending on current animation update the sprite offset.
*/
function tickMultispriteAnimation(sprite) {


    var curAnim = sprite.animations.name;

    // The frame names are ???/001.png etc.
    if (this._sprite.frameName === undefined) {
        console.error('Soll nicht passieren');
    }
    var start = this._sprite.frameName.length - 7;
    var frameNumber = this._sprite.frameName.substring(start, start + 3);
    var curFrame = parseInt(frameNumber, 10);

    this._multiSprites.forEach(function (ms) {

        // Get the current sub sprite anim name.
        let subPos = this._getSubspriteOffset(ms.name, curAnim, curFrame);

        ms.sprite.position = {
            x: subPos.x,
            y: subPos.y
        };

    }, this);
}

/**
 * Helper function since this must be called from multiple places.
 * 
 * @param mainAnimName
 */
export function playSubspriteAnimation(sprite, mainAnimName) {
    // Iterate over all subsprites an set their animations.
    sprite._multiSprites.forEach(function (s) {
        let subAnim = this._getSubspriteAnimation(s.name, mainAnimName);
        if (subAnim === null) {
            // no suitable sub animation found. Do nothing.
            return;
        }
        s.sprite.play(subAnim);

    }, this);
}