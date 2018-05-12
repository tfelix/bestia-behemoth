import { Point } from "../../entities/Point";
import { Px } from "../../entities/Px";

export class MapHelper {

  public static readonly TILE_SIZE_PX = 32;

  static pointToPixel(p: Point): Px {
    return new Px(
      p.x * this.TILE_SIZE_PX,
      p.y * this.TILE_SIZE_PX
    );
  }

  static getTileXY(xPx: number, yPx: number): Point {
    return new Point(
      Math.floor(xPx / this.TILE_SIZE_PX),
      Math.floor(yPx / this.TILE_SIZE_PX)
    );
  }

  static getClampedTilePixelXY(xPx: number, yPx: number): Px {
    return new Px(
      Math.floor(xPx / this.TILE_SIZE_PX) * this.TILE_SIZE_PX,
      Math.floor(yPx / this.TILE_SIZE_PX) * this.TILE_SIZE_PX
    );
  }
}