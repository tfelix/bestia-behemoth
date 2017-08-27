import Renderer from './Render';
import LOG from '../../util/Log';

/**
 * The weather renderer controls the rain effect. The rain intensity controls the
 * amount of rain. Basically the rule of thumb is:
 * <p>
 * intensity = 0, No rain 0 < intensity < 0.3: Light rain. 0.3 < intensity <
 * 0.7: Heavier rain. 0.7 < intensity < 0.9: Storm. intensity > 0.9 : HEAVY
 * Storm.
 * </p>
 * 
 * It keeps track about updates of the weather situation for the current bestia and displays it.
 * 
 */
export default class WeatherRenderer extends Renderer {

    constructor() {


    }

    load() {
        LOG.info('Weather loaded.');
    }
}