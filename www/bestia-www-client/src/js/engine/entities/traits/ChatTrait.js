import Trait from './Trait';
import Signal from '../../../io/Signal';
import { entityCache } from '../../EngineData';
import LOG from '../../../util/Log';

const CHAT_DISPLAY_DURATION_MS = 3500;
const SPRITE_Y_OFFSET = 90;

const CHAT_STYLE = Object.freeze({
    font: '12px Arial',
    fill: '#ffffff',
    boundsAlignH: 'center',
    boundsAlignV: 'middle'
});

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

        this._pubsub.subscribe(Signal.CHAT_RECEIVED, this._onChatMsgHandler, this);
    }

    /**
     * Saves the incoming chat message.
     */
    _onChatMsgHandler(_, msg) {

        // Message must be directed to an actual entity.
        if (!msg.entityId) {
            return;
        }

        LOG.debug('Incoming chat message for entity: ' + msg.entityId);
        
        var entity = entityCache.getEntity(msg.entityId);
        if (!entity) {
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

        this._createChatVisual(sprite, text);
    }

    _createChatVisual(sprite, text) {
        // Check if there is already a chat sprite attached. 
        if (sprite.chatMsg) {
            sprite.chatMsg.destroy();
            delete sprite.chatMsg;
        }

        let box = this._game.add.graphics(0, 0);
        // Prevent scaling by model.
        box.beginFill(0x000000);
        box.alpha = 0.8;
        // Workaround since scale min max ist not available for graphics.
        let textSprite = this._game.add.text(5, 1, text, CHAT_STYLE);
        textSprite.setScaleMinMax(1, 1);

        box.drawRect(0, 0, textSprite.width + 10, textSprite.height - 3);
        box.addChild(textSprite);
        box.anchor.set(0.5);

        box.scale.set(1 / sprite.scale.x, 1 / sprite.scale.y);

        // Add chat msg and keep reference.
        sprite.addChild(box);
        box.position.x = - Math.round(box.width / 2);
        box.position.y = - (sprite.height + SPRITE_Y_OFFSET);

        sprite.chatMsg = box;

        this._game.time.events.add(CHAT_DISPLAY_DURATION_MS, function () {
            // Only destroy the chat message if it was not yet replaced by another one.
            if (box.alive) {
                box.destroy();
                delete sprite.chatMsg;
            }
        }, this);
    }
}
