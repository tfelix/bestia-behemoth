import { Px, Size, Point } from 'model';
import { MapHelper } from 'map';

export class SpriteHelper {

  private readonly sizeCache = new Map<string, Size>();

  constructor(
    private readonly game: Phaser.Scene
  ) {
  }

  public getSpriteSize(sprite: Phaser.GameObjects.Sprite): Size {
    const name = sprite.texture.key;
    if (this.sizeCache.has(name)) {
      return this.sizeCache.get(name);
    }

    const size = this.determineSpriteSize(sprite);
    this.sizeCache.set(name, size);
    return size;
  }

  private determineSpriteSize(sprite: Phaser.GameObjects.Sprite) {
    const tempSprite = this.game.add.sprite(-100, -100, sprite.texture.key);
    let size: Size;
    try {
      tempSprite.anims.play(`${sprite.texture.key}_stand_down`);
      size = new Size(tempSprite.width, tempSprite.height);
      tempSprite.destroy();
    } catch (_) {
      size = new Size(tempSprite.width, tempSprite.height);
    }

    return size;
  }

  public getSpriteTopLeftPoint(
    sprite: Phaser.GameObjects.Sprite
  ): Point {
    const topleft = sprite.getTopLeft();
    return new Point(
      Math.floor(topleft.x / MapHelper.TILE_SIZE_PX),
      Math.floor(topleft.y / MapHelper.TILE_SIZE_PX)
    );
  }

  public getSpriteSizePoints(
    sprite: Phaser.GameObjects.Sprite
  ): Size {
    const size = this.getSpriteSize(sprite);

    return new Size(
      Math.ceil(size.width / MapHelper.TILE_SIZE_PX),
      Math.ceil(size.height / MapHelper.TILE_SIZE_PX)
    );
  }
}
