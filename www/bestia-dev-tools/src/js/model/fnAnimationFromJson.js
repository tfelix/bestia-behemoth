import Animation from './Animation';

/**
 * Holds information about a bestia animation for a sprite.
 * 
 * @export
 * @param json - The JSON containing the animation data.
 * @return Animation format for bestia filled with info from JSON.
 */
export default (json) =>  {
    
    let anim = new Animation();
    anim.from(json.from);
    anim.to(json.to);
    anim.fps(json.fps);
    anim.name(json.name);

    return anim;
};
