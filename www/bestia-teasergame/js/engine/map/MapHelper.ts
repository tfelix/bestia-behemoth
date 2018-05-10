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

  static pixelToPoint(xPx: number, yPx: number) {
    return new Point(
      Math.floor(xPx / this.TILE_SIZE_PX),
      Math.floor(yPx / this.TILE_SIZE_PX)
    );
  }
}