import * as LOG from 'loglevel';

import { Pointer } from './Pointer';
import { PointerManager } from './PointerManager';
import { EngineContext } from '../EngineContext';
import { Point, Px } from 'model';
import { MapHelper } from '../map/MapHelper';

export class MovePointer extends Pointer {

  private marker: Phaser.GameObjects.Sprite;

  constructor(
    manager: PointerManager,
    ctx: EngineContext
  ) {
    super(manager, ctx);
  }

  public activate() {
    this.marker.visible = true;
    this.ctx.game.input.on('pointerdown', this.onClickMove, this);
  }

  public updatePosition(point: Point) {
    this.marker.setPosition(point.x, point.y);
  }

  private onPathFound(path) {
    LOG.debug(`Path found: ${JSON.stringify(path)}`);
    path = path || [];
    if (path.length === 0) {
      return;
    }

    // Remove first element since its the current position.
    path.shift();

    /*
    var pbid = this._playerBestia.playerBestiaId();
    var eid = this._playerBestia.entityId();
    var speed = this._playerBestia.statusBasedValues.walkspeed();
    */

    // addMoveComponent(this._playerEntity, path, speed);
  }

  private onClickMove(pointer: Phaser.Input.Pointer) {
    if (!pointer.leftButtonDown) {
      return;
    }

    const goal = MapHelper.pixelToPoint(pointer.downX, pointer.downY);
    const start = { x: 0, y: 0 };
    LOG.debug(`Find path from: ${JSON.stringify(start)} to ${JSON.stringify(goal)}`);
    this.ctx.pathfinder.findPath(start.x, start.y, goal.x, goal.y, this.onPathFound.bind(this));
  }

  public load(loader) {
    this.ctx.game.load.spritesheet(
      'indicator_move',
      '../assets/sprites/indicators/cursor.png',
      { frameWidth: 32, frameHeight: 32, startFrame: 0, endFrame: 1 }
    );
  }

  public create() {
    this.marker = this.ctx.game.add.sprite(100, 100, 'indicator_move');
    this.marker.setOrigin(0, 0);
    const config = {
      key: 'cursor_anim',
      frames: this.ctx.game.anims.generateFrameNumbers('indicator_move', { start: 0, end: 1 }),
      frameRate: 1,
      repeat: -1
    };
    this.ctx.game.anims.create(config);
    this.marker.anims.play('cursor_anim');
    this.marker.visible = false;
  }
}
