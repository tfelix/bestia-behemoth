import { MapHelper } from 'map';

import { EngineContext } from '../EngineContext';
import { Point } from 'model';

export class CollisionRenderer {

  private graphicsCollision: Phaser.GameObjects.Graphics | null = null;
  private graphicsNonCollision: Phaser.GameObjects.Graphics | null = null;

  private rect = new Phaser.Geom.Rectangle(0, 0, MapHelper.TILE_SIZE_PX, MapHelper.TILE_SIZE_PX);

  private widthTiles: number;
  private heightTiles: number;

  constructor(
    private readonly context: EngineContext
  ) {

    // Generalize this map size (also in CollisionUpdater.ts)
    const width = 800;
    const height = 600;
    this.widthTiles = Math.ceil(width / MapHelper.TILE_SIZE_PX);
    this.heightTiles = Math.ceil(height / MapHelper.TILE_SIZE_PX);
  }

  public update() {
    if (!this.context.config.debug.renderCollision) {
      if (this.graphicsCollision !== null) {
        this.clearData();
      }
      return;
    } else {
      this.renderCollision();
    }
  }

  private renderCollision() {
    this.prepareData();

    this.graphicsCollision.clear();
    this.graphicsNonCollision.clear();

    for (let y = 0; y < this.heightTiles; y++) {
      for (let x = 0; x < this.widthTiles; x++) {
        const px = MapHelper.pointToPixel(new Point(x, y));
        this.rect.x = px.x;
        this.rect.y = px.y;
        const hasCollision = this.context.collisionUpdater.hasCollision(x, y);
        if (hasCollision) {
          this.graphicsCollision.fillRectShape(this.rect);
        } else {
          this.graphicsNonCollision.fillRectShape(this.rect);
        }
      }
    }
  }

  private prepareData() {
    if (this.graphicsCollision) {
      return;
    }
    this.graphicsCollision = this.context.game.add.graphics({ fillStyle: { color: 0x0000FF } });
    this.graphicsNonCollision = this.context.game.add.graphics({ fillStyle: { color: 0x00FF00 } });
    this.graphicsCollision.depth = 1000000;
    this.graphicsNonCollision.depth = 1000000;
    this.graphicsCollision.alpha = 0.3;
    this.graphicsNonCollision.alpha = 0.3;
  }

  private clearData() {
    this.graphicsCollision.destroy();
    this.graphicsNonCollision.destroy();
    this.graphicsCollision = null;
    this.graphicsNonCollision = null;
  }
}
