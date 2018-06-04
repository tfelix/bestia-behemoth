import * as LOG from 'loglevel';

import { Pointer } from './Pointer';
import { PointerManager } from './PointerManager';
import { EngineContext } from '../EngineContext';
import { Point, Px } from 'model';
import { MapHelper } from '../map/MapHelper';
import { ComponentType, PositionComponent, MoveComponent } from 'entities/components';

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

  private onPathFound(path: Array<{ x: number; y: number }>) {
    LOG.debug(`Path found: ${JSON.stringify(path)}`);
    path = path || [];
    if (path.length === 0) {
      return;
    }

    const playerEntityId = this.ctx.playerHolder.activeEntity.id;
    const move = new MoveComponent(
      1000,
      playerEntityId
    );
    move.walkspeed = 1;
    move.path = path.map(p => new Point(p.x, p.y));
    this.ctx.entityStore.addComponent(move);
  }

  private onClickMove(pointer: Phaser.Input.Pointer) {
    if (!pointer.leftButtonDown) {
      return;
    }

    const activePlayerEntity = this.ctx.playerHolder.activeEntity;
    if (!activePlayerEntity) {
      return;
    }
    const playerPositionComponent = activePlayerEntity.getComponent(ComponentType.POSITION) as PositionComponent;
    if (!playerPositionComponent) {
      return;
    }
    const start = playerPositionComponent.position;
    const goal = MapHelper.pixelToPoint(pointer.downX, pointer.downY);

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
