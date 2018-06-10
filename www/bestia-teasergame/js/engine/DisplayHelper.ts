import { MapHelper } from 'map';
import { Size } from 'model';

export class DisplayHelper {

  private readonly width = 800;
  private readonly height = 600;

  constructor(
    private readonly scene: Phaser.Scene
  ) {

  }

  public getDisplaySizeInTiles(): Size {
    const widthTiles = Math.ceil(this.width / MapHelper.TILE_SIZE_PX);
    const heightTiles = Math.ceil(this.height / MapHelper.TILE_SIZE_PX);
    return new Size(widthTiles, heightTiles);
  }
}
