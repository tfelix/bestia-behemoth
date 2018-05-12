import { Pointer } from "./Pointer";
import { PointerManager } from "./PointerManager";
import { EngineContext } from "../EngineContext";
import { Point } from "../../entities/Point";
import { Px } from "../../entities/Px";

/**
 * Basic indicator for visualization of the mouse pointer.
 * 
 * @class Bestia.Engine.Indicator
 */
export class MovePointer extends Pointer {

  private marker: Phaser.GameObjects.Sprite;

  constructor(
    manager: PointerManager,
    ctx: EngineContext
  ) {
    super(manager, ctx);
    /*
        this._effect = null;
    
        // this._pubsub = engineContext.pubsub;
        // this._game = engineContext.game;
    
        // this._playerBestia = null;
        // this._playerEntity = null;
        this._pubsub.subscribe(Signal.BESTIA_SELECTED, function (_, bestia) {
          LOG.debug('MoveIndivator: New Bestia detected.');
          this._playerBestia = bestia;
        }, this);
    
        // Catch the currently selected entity if its getting updated.
        this._pubsub.subscribe(Signal.ENTITY_UPDATE, function (_, entity) {
          if (this._playerBestia && entity.eid === this._playerBestia.entityId()) {
            this._playerEntity = entity;
          }
        }, this);
        */
  }

  activate() {
    this.marker.visible = true;
  }

  updatePosition(point: Point, px: Px) {
    this.marker.setPosition(point.x, point.y);
  }

  private onPathFound(path) {
    /*
    path = path || [];

    if (path.length === 0) {
      return;
    }

    // Remove first element since its the current position.
    path.shift();

    var pbid = this._playerBestia.playerBestiaId();
    var eid = this._playerBestia.entityId();
    var speed = this._playerBestia.statusBasedValues.walkspeed();

    var msg = new Message.EntityMove(pbid,
      eid,
      path,
      speed);
    this._pubsub.send(msg);*/

    // Start movement locally as well.
    // addMoveComponent(this._playerEntity, path, speed);
  }

  _onClick(pointer) {
/*
    // No player no movement.
    if (!this._playerBestia) {
      return;
    }

    // Only left button.
    if (pointer.button !== Phaser.Mouse.LEFT_BUTTON) {
      return;
    }

    // Display fx.
    this._effect.alpha = 1;
    this._game.add.tween(this._effect).to({
      alpha: 0
    }, 500, Phaser.Easing.Cubic.Out, true);

    var goal = WorldHelper.getTileXY(pointer.worldX, pointer.worldY);

    // Start the path calculation
    this._ctx.pathfinder.findPath(this._playerBestia.position(), goal, this._onPathFound.bind(this));
  */
 }

	/**
	 * Override an create all needed game objects here.
	 */
  load(loader) {
    this.ctx.game.load.spritesheet(
      'indicator_move',
      '../assets/sprites/indicators/cursor.png',
      { frameWidth: 32, frameHeight: 32, startFrame: 0, endFrame: 1 }
    );
  }

	/**
	 * Override an create all needed game objects here.
	 */
  create() {
    this.marker  = this.ctx.game.add.sprite(100, 100, 'indicator_move');
    this.marker.setOrigin(0, 0);
    var config = {
      key: 'cursor_anim',
      frames: this.ctx.game.anims.generateFrameNumbers('indicator_move', { start: 0, end: 1 }),
      frameRate: 1,
      repeat: -1
    };
    this.ctx.game.anims.create(config);
    this.marker.anims.play('cursor_anim');
    this.marker.visible = false;
    
    /*
    const graphics = this.ctx.game.add.graphics(0, 0);
    graphics.beginFill(0x42D65D);
    graphics.drawRect(0, 0, 32, 32);
    graphics.endFill();
    graphics.alpha = 0;
    this.marker = graphics;
    this.marker.addChild(this._effect);
    */
  }
}