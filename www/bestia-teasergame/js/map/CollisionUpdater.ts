import * as LOG from 'loglevel';

import { MapHelper } from 'map';
import { EngineContext } from 'engine/EngineContext';
import { ComponentType, VisualComponent, PositionComponent } from 'entities/components';
import { SpriteDescription, getSpriteDescriptionFromCache } from 'engine/renderer';
import { Point, Size } from 'model';

export class CollisionUpdater {

  private widthTiles: number;
  private heightTiles: number;
  private collisionMap: number[][];
  public isDirty = true;

  constructor(
    private readonly ctx: EngineContext
  ) {
    // Generalize this map size
    const width = 800;
    const height = 600;
    this.widthTiles = Math.ceil(width / MapHelper.TILE_SIZE_PX);
    this.heightTiles = Math.ceil(height / MapHelper.TILE_SIZE_PX);

    LOG.debug(`Found collision map size: w:${this.widthTiles}, h: ${this.heightTiles}`);

    this.collisionMap = new Array(this.heightTiles);
    this.clearCollisionMap();
    ctx.pathfinder.setGrid(this.collisionMap);
    ctx.pathfinder.setAcceptableTiles(0);
  }

  private clearCollisionMap() {
    for (let i = 0; i < this.collisionMap.length; i++) {
      const element = new Array(this.widthTiles);
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

      const sprite = entity.gameData.visual.sprite;
      const spriteTopLeft = getSpriteTopLeft(sprite);
      const spriteSize = getSpriteDimensions(sprite);

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

function getSpriteTopLeft(
  sprite: Phaser.GameObjects.Sprite
): Point {
  const topleft = sprite.getTopLeft();
  return new Point(
    Math.floor(topleft.x / MapHelper.TILE_SIZE_PX),
    Math.floor(topleft.y / MapHelper.TILE_SIZE_PX)
  );
}

function getSpriteDimensions(
  sprite: Phaser.GameObjects.Sprite
): Size {
  return new Size(
    Math.ceil(sprite.width / MapHelper.TILE_SIZE_PX),
    Math.ceil(sprite.height / MapHelper.TILE_SIZE_PX)
  );
}
