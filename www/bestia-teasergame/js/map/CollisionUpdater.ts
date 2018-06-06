import * as LOG from 'loglevel';

import { MapHelper } from 'map';
import { EngineContext } from 'engine/EngineContext';
import { ComponentType, VisualComponent, PositionComponent } from 'entities/components';
import { SpriteDescription } from 'engine/renderer';

export class CollisionUpdater {

  private collisionMap: number[][];
  public isDirty = true;

  constructor(
    private readonly ctx: EngineContext
  ) {
    // Generalize this map size
    const width = 800;
    const height = 600;
    const widthTiles = Math.ceil(width / MapHelper.TILE_SIZE_PX);
    const heightTiles = Math.ceil(height / MapHelper.TILE_SIZE_PX);

    LOG.debug(`Found collision map size: w:${widthTiles}, h: ${heightTiles}`);

    this.collisionMap = new Array(heightTiles);
    for (let i = 0; i < this.collisionMap.length; i++) {
      const element = new Array(widthTiles);
      element.fill(0);
      this.collisionMap[i] = element;
    }
    ctx.pathfinder.setGrid(this.collisionMap);
    ctx.pathfinder.setAcceptableTiles(0);
  }

  public update() {
    this.ctx.pathfinder.calculate();
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

      const sprite = visualComp.sprite;
      const spriteDesc = '';
      const desc = this.ctx.game.cache.json.get(spriteDesc) as SpriteDescription;
      const collision = desc.collision || [[]];

    });

    this.isDirty = false;
  }

  public hasCollision(x: number, y: number): boolean {
    return this.collisionMap[y][x] !== 0;
  }
}
