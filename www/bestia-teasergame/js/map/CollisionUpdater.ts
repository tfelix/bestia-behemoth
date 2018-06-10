import * as LOG from 'loglevel';

import { MapHelper } from 'map';
import { EngineContext } from 'engine/EngineContext';
import { ComponentType, VisualComponent, PositionComponent } from 'entities/components';
import { SpriteDescription, getSpriteDescriptionFromCache } from 'engine/renderer';
import { Point, Size } from 'model';

export class CollisionUpdater {

  private collisionMap: number[][];
  public isDirty = true;
  private displayTileSize: Size;

  constructor(
    private readonly ctx: EngineContext
  ) {

    this.displayTileSize = ctx.helper.display.getDisplaySizeInTiles();
    LOG.debug(`Found collision map size: w: ${this.displayTileSize.width}, h: ${this.displayTileSize.height}`);

    this.collisionMap = new Array(this.displayTileSize.height);
    this.clearCollisionMap();
    ctx.pathfinder.setGrid(this.collisionMap);
    ctx.pathfinder.setAcceptableTiles(0);
  }

  private clearCollisionMap() {
    for (let i = 0; i < this.collisionMap.length; i++) {
      const element = new Array(this.displayTileSize.width);
      element.fill(0);
      this.collisionMap[i] = element;
    }
  }

  public update() {
    this.ctx.pathfinder.calculate();

    // TODO Do this only if a entity has changed.
    // This is for now and inefficent.
    this.isDirty = true;
    this.clearCollisionMap();
    this.updateCollisionMap();
  }

  public updateCollisionMap() {
    if (!this.isDirty) {
      return;
    }

    this.ctx.entityStore.entities.forEach(entity => {
      const visualComp = entity.getComponent(ComponentType.VISUAL) as VisualComponent;
      const positionComp = entity.getComponent(ComponentType.POSITION) as PositionComponent;
      if (!visualComp || !positionComp || !visualComp.visible) {
        return;
      }

      const spriteName = visualComp.sprite;
      const spriteDesc = getSpriteDescriptionFromCache(spriteName, this.ctx.game);
      const collision = spriteDesc && spriteDesc.collision || [[]];

      const sprite = entity.data.visual.sprite;
      const spriteTopLeft = this.ctx.helper.sprite.getSpriteTopLeftPoint(sprite);
      const spriteSize = this.ctx.helper.sprite.getSpriteSizePoints(sprite);

      for (let dy = 0; dy < collision.length; dy++) {
        for (let dx = 0; dx < collision[dy].length; dx++) {
          if (collision[dy][dx] === 1) {
            const x = spriteTopLeft.x + dx;
            const y = spriteTopLeft.y + dy;
            this.collisionMap[y][x] = 1;
          }
        }
      }
    });

    this.ctx.pathfinder.setGrid(this.collisionMap);
    this.isDirty = false;
  }

  public hasCollision(x: number, y: number): boolean {
    return this.collisionMap[y][x] !== 0;
  }
}
