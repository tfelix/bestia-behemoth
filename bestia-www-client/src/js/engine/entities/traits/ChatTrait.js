import Trait from './Trait';
import Signal from '../../../io/Signal';
import { entityCache } from '../../EngineData';
import LOG from '../../../util/Log';

/**
 * Renders a chat message to the client if there is chat data associated with an entity.
 */
export class ChatTrait extends Trait {

    constructor(game, pubsub) {
        super();

        if(!game) {
            throw 'game can not be null.';
        }

        if(!pubsub) {
            throw 'pubsub can not be null.';
        }

        this._pubsub = pubsub;
        this._game = game;

        this._pubsub.subscribe(Signal.CHAT_RECEIVED, this._onChatMsgHandler, this);
    }

    /**
     * Saves the incoming chat message.
     */
    _onChatMsgHandler(_, msg) {

        // Message must be directed to an actual entity.
        if(!msg.entityId) {
            return;
        }

        LOG.debug('Incoming chat message for entity: ' + msg.entityId);
        var entity = entityCache.getEntity(msg.entityId);
        if(!entity) {
            LOG.debug('Entity not found. Cant add chat message.');
            return;
        }

        entity.chatMsg = msg.text();
    }

    /**
     * Checks if the given entity contains the movement trait.
     * @param {object} entity Entity object. 
     */
    hasTrait(entity) {
        return entity.hasOwnProperty('chatMsg');
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
        if(sprite.chatMsg) {
            LOG.debug('Added chat message! ' + text);
        } else {
            // Create new chat sprite and attach it.
            LOG.debug('Added chat message 2! ' + text);
        }
    }

}
