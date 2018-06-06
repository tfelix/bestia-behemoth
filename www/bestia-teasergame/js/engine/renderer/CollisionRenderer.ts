import { MapHelper } from 'map';

import { EngineContext } from '../EngineContext';

class CollisionRenderer {

  private graphics: Phaser.GameObjects.Graphics | null = null;
  private rect = new Phaser.Geom.Rectangle(0, 0, MapHelper.TILE_SIZE_PX, MapHelper.TILE_SIZE_PX);

  constructor(
    private readonly context: EngineContext
  ) {

  }

  public update() {
    if (!this.context.config.debug.renderCollision) {
      if (this.graphics !== null) {
        this.clearData();
      }
      return;
    } else {
      this.renderCollision();
    }
  }

  private renderCollision() {

  }

  private clearData() {
    this.graphics.destroy();
    this.graphics = null;
  }
}
