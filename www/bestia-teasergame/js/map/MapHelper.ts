import { Point, Px } from 'model';

export class MapHelper {

  public static readonly TILE_SIZE_PX = 32;

  public static pointToPixelCentered(p: Point): Px {
    return new Px(
      p.x * this.TILE_SIZE_PX + this.TILE_SIZE_PX / 2,
      p.y * this.TILE_SIZE_PX + this.TILE_SIZE_PX / 2
    );
  }

  public static pointToPixel(p: Point): Px {
    return new Px(
      p.x * this.TILE_SIZE_PX,
      p.y * this.TILE_SIZE_PX
    );
  }

  public static pixelToPoint(xPx: number, yPx: number): Point {
    return new Point(
      Math.floor(xPx / this.TILE_SIZE_PX),
      Math.floor(yPx / this.TILE_SIZE_PX)
    );
  }

  public static getClampedTilePixelXY(xPx: number, yPx: number): Px {
    return new Px(
      Math.floor(xPx / this.TILE_SIZE_PX) * this.TILE_SIZE_PX,
      Math.floor(yPx / this.TILE_SIZE_PX) * this.TILE_SIZE_PX
    );
  }
}
