import Trait from './Trait';
import MID from '../../io/messages/MID.js';
import { entityCache } from '../../EngineData';
import LOG from '../../../util/Log';


/**
 * Contains the textual styles of the damage entity display. It will be
 * displayed as text.
 */

const NORMAL = {
    font: '18px Arial',
    fill: '#ffffff',
    align: 'center',
    stroke: '#000000',
    strokeThickness: 3
};

const CRIT = {
    font: '18px Arial',
    fill: '#ffffff',
    align: 'center',
    stroke: '#000000',
    strokeThickness: 3
};

const HEAL = {
    font: '18px Arial',
    fill: '#ffffff',
    align: 'center',
    stroke: '#000000',
    strokeThickness: 3
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

        this._pubsub.subscribe(MID.ENTITY_DAMAGE, this._onDamageMsgHandler, this);
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
            LOG.debug('Entity not found. Cant add damage.');
            return;
        }

        if (!entity.hasOwnProperty('damage')) {
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
        dmgs.forEach(function (x) {

            // See if there is an entity existing to display this damage.
            let entity = this._entityCache.getEntity(msg.eid);

            // No entity, no dmg.
            if (entity === null) {
                return;
            }

            let dmgFx = this._manager.getCachedEffect(CACHE_KEY);
            let pos = entity.getPositionPx();

            if (!dmgFx) {
                // Create a new instance of the entity.
                dmgFx = new TextEntity(this._ctx);
                dmgFx.setPositionPx(pos.x, pos.y);
                dmgFx.addToGame();
            } else {
                dmgFx.setPositionPx(pos.x, pos.y);
                dmgFx.getRootVisual().alpha = 1;
            }

            dmgFx.setText(x.dmg);

            switch (x.t) {
                case 'HEAL':
                    dmgFx.setStyle(HEAL);
                    break;
                case 'MISS':
                case 'HIT':
                    dmgFx.setStyle(NORMAL);
                    break;
                case 'CRITICAL':
                    dmgFx.setStyle(CRIT);
                    break;
                default:
                    LOG.warn('Unknown damage type. Using normal style.');
                    dmgFx.setStyle(NORMAL);
                    break;
            }

            // Start the display animation.
            this._startAnimation(entity, dmgFx);

        }, this);
    }

    _startAnimation(targetSprite, dmg) {

        let targetSize = targetEntity.getSize();
        let pos = targetEntity.getPositionPx();

        let cords = {
            x: [pos.x, pos.x - targetSize.width * 0.75, pos.x - targetSize.width * 1.5],
            y: [pos.y - targetSize.height * 0.80, pos.y - targetSize.height * 1.5, pos.y]
        };
        var tween = this._game.add.tween(dmg.getRootVisual()).to(cords, 850);
        tween.interpolation(function (v, k) {
            return Phaser.Math.bezierInterpolation(v, k);
        });

        // add entity back to cache when tween finished.
        tween.onComplete.add(function () {
            this._manager.cacheEffect(CACHE_KEY, dmg);
        }, this);

        // Add alpha fade.
        this._game.add
            .tween(dmg.getRootVisual())
            .to({ alpha: 0 }, 100, Phaser.Easing.Linear.None, true, 850)
            .start();
        // Start animation.
        tween.start();
    }

    _createAnimation() {

        var tween = this._game.add.tween(this._);

    }
}
