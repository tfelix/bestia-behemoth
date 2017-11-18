import 'knockout';
import Sprite from './Sprite';
import { animationFromJson } from './fnAnimationFromJson';

/**
 * This function will transform a incoming bestia json format into a sprite object.
 * 
 * @param json - Incoming json describing the bestia object.
 * @returns The created sprite object.
 */
export default (json) =>  {

    let sprite = new Sprite();

    sprite.name(json.name);
    sprite.type(json.type);
    sprite.version(json.version);
    sprite.scale(json.scale);
    sprite.anchor(json.anchor);

    // Handle the animations.
    let anims = [];
    json.animations.forEach(el => {
        anims.push(animationFromJson(el));
    });

    sprite.animations = ko.observableArray(anims);

    return sprite;
}