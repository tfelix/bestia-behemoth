import Trait from './Trait';
import Signal from '../../../io/Signal';
import { entityCache } from '../../EngineData';
import LOG from '../../../util/Log';


/**
 * Contains the textual styles of the damage entity display. It will be
 * displayed as text.
 */

const NORMAL = {
	font : "18px Arial",
	fill : "#ffffff",
	align : "center",
	stroke : '#000000',
	strokeThickness : 3
};

const CRIT = {
	font : "18px Arial",
	fill : "#ffffff",
	align : "center",
	stroke : '#000000',
	strokeThickness : 3
};

const HEAL = {
	font : "18px Arial",
	fill : "#ffffff",
	align : "center",
	stroke : '#000000',
	strokeThickness : 3
};

/**
 * Renders a chat message to the client if there is chat data associated with an entity.
 */
export class ChatTrait extends Trait {

    constructor(game, pubsub) {
        super();

        if (!game) {
            throw 'game can not be null.';
        }

        if (!pubsub) {
            throw 'pubsub can not be null.';
        }

        this._pubsub = pubsub;
        this._game = game;

        this._pubsub.subscribe(Signal.CHAT_RECEIVED, this._onDamageMsgHandler, this);
    }

    /**
     * Saves the incoming chat message.
     */
    _onDamageMsgHandler(_, msg) {

        // Message must be directed to an actual entity.
        if (!msg.entityId) {
            return;
        }

        LOG.debug('Incoming damage message for entity: ' + msg.entityId);
        var entity = entityCache.getEntity(msg.entityId);
        if (!entity) {
            LOG.debug('Entity not found. Cant add damage message.');
            return;
        }

        if(!entity.hasOwnProperty('damage')) {
            entity.damage = [];
        }

        entity.damage = entity.damage.concat(msg.d);
    }

    /**
     * Checks if the given entity contains the movement trait.
     * @param {object} entity Entity object. 
     */
    hasTrait(entity) {
        return entity.hasOwnProperty('damage');
    }

    /**
     * Handle the event if a entity has a special trait attached to its datastructure.
     * @param {object} entity Entity object describing the entity.
     * @param {PhaserJS.Sprite} sprite Sprite object from PhaserJS.
     */
    handleTrait(entity, sprite) {
        let text = entity.chatMsg;
        delete entity.chatMsg;

        // Check if there is already a chat sprite attached. 
        if (sprite.chatMsg) {
            sprite.chatMsg.destroy();
            delete sprite.chatMsg;
        }

        this._createChatVisual(sprite, text);
    }
}
