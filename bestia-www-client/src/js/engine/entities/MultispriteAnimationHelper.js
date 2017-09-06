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


export function addSubsprite(sprite, subsprite) {
    if (!sprite.hasOwnProperty('_subsprites')) {
        sprite._subsprites = [];
    }
    // Hold ref to subsprite in own counter so we can faster
    // iterate over all added subsprites.
    sprite._subsprites.push(subsprite);
    sprite.addChild(subsprite);

    // Save the multisprite data to the phaser sprite.
    // maybe we can centralize this aswell.
    subsprite._subspriteData = msData;
}

/**
 * Depending on the animation name of the main sprite all the subsprites of this 
 * sprite will try to play their animation aswell. After the animation playback 
 * has started for each frame should the sprite offsets be tested so the child
 * sprite position is kept in sync with the parent sprite position.
 * 
 * @param {PhaserJS.Sprite} sprite 
 * @param {String} mainAnimName - Current main animation played on main, parent sprite.
 */
export function playSubspriteAnimation(sprite, mainAnimName) {

    if (!sprite.hasOwnProperty('_subsprites')) {
        LOG.warn('Sprite has now subsprites attached. Can not play subsprite animation.');
        return;
    }

    // Iterate over all subsprites an set their animations.
    sprite._subsprites.forEach(function (s) {
        let subAnim = _getSubspriteAnimation(s._subspriteData.name, mainAnimName);
        
        if (subAnim === null) {
            // no suitable sub animation found. Do nothing.
            LOG.warn('No data found for subsprite: '+ s._subspriteData.name +' animation: ' + mainAnimName);
            return;
        }

        s.animations.play(subAnim);

    }, this);
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
function _getSubspriteAnimation(subspriteName, currentAnim) {

    let subsprite = _getSubspriteData(subspriteName);

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
 * Searches for the data of the subsprite.
 */
function _getSubspriteData(subspriteName) {
    
    for (let i = 0; i < this._multiSprites.length; i++) {
        if (this._multiSprites[i].name === subspriteName) {
            return this._multiSprites[i];
        }
    }
    return null;
}