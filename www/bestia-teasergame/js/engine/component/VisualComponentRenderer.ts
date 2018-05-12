import * as LOG from 'loglevel';

import { ComponentRenderer } from './ComponentRenderer';
import { Component } from '../../entities/components/Component';
import { Entity } from '../../entities/Entity';
import { VisualComponent, SpriteType } from '../../entities/components/VisualComponent';
import { PositionComponent } from '../../entities/components/PositionComponent';
import { MapHelper } from '../map/MapHelper';
import { Point } from '../../entities/Point';
import { ComponentType } from '../../entities/components/ComponentType';

class VisualGameData { }

interface SpriteAnimation {
  name: string;
  from: number;
  to: number;
  fps: number;
}

interface SpriteDescription {
  name: string;
  type: SpriteType;
  version: number;
  scale: number;
  animations: SpriteAnimation[];
  anchor: Point;
  multiSprite: string[];
}

export class VisualComponentRenderer extends ComponentRenderer<VisualComponent> {

  public static readonly DAT_SPRITE = 'sprite';

  constructor(game: Phaser.Scene) {
    super(game);
  }

  get supportedComponent(): ComponentType {
    return ComponentType.VISUAL;
  }

  protected createGameData(entity: Entity, component: VisualComponent) {

    const texture = this.game.textures.get(component.sprite);
    const desc = (texture.customData as any).meta.description;

    LOG.debug(`Building: ${JSON.stringify(component)} (dynamic sprite)`);

    const posComp = entity.getComponent(ComponentType.POSITION) as PositionComponent;
    const px = MapHelper.getClampedTilePixelXY(posComp.position.x, posComp.position.y);
    const sprite = this.game.add.sprite(px.x, px.y, desc.name);
    sprite.setOrigin(0, 1);
    entity.gameData[VisualComponentRenderer.DAT_SPRITE] = sprite;

    this.setupSpriteAnimation(sprite, desc);

    // Add the multi sprites if there are some of them.
    const multisprites = desc.multiSprite || [];

    multisprites.forEach(multiSprite => {
      LOG.debug(`Adding multisprite to main sprite: ${component.sprite}`);

      // Get the desc file of the multisprite.
      const msDescName = `${multiSprite}_desc`;
      const msDesc = this.game.cache.json.get(msDescName) as SpriteDescription;

      // Was not loaded. Should not happen.
      if (msDesc === null) {
        LOG.warn(`Subsprite description was not loaded. This should not happen: ${msDescName}`);
        return;
      }

      // Generate offset information.
      const offsetFileName = this.getOffsetFilename(multiSprite, desc.name);
      const offsets = this.game.cache.json.get(offsetFileName) || {};

      const msSprite = this.game.add.sprite(0, 0, multiSprite);

      const defaultCords = offsets.defaultCords || {
        x: 0,
        y: 0
      };
      msSprite.x = defaultCords.x;
      msSprite.y = defaultCords.y;

      // Setup the normal data.
      this.setupSpriteAnimation(msSprite, msDesc);

      // addSubsprite(sprite, msSprite);
    });
  }

  protected updateGameData(entity: Entity, component: VisualComponent) {
    LOG.debug('Updating not implemented.');
  }

  protected removeComponent(entity: Entity, component: Component) {
  }

  private getOffsetFilename(multispriteName, mainspriteName) {
    return `offset_${multispriteName}_${mainspriteName}`;
  }

  private setupScaleAndOrigin(sprite: Phaser.GameObjects.Sprite, description: SpriteDescription) {
    // Setup the normal data.
    const anchor = description.anchor || {
      x: 0.5,
      y: 0.5
    };
    sprite.displayOriginX = anchor.x;
    sprite.displayOriginY = anchor.y;

    const scale = description.scale || 1;
    sprite.scaleX = scale;
    sprite.scaleY = scale;
  }

  /**
   * Helper function to setup a sprite with all the information contained
   * inside a description object.
   */
  private setupSpriteAnimation(sprite: Phaser.GameObjects.Sprite, description: SpriteDescription) {
    const anims = description.animations || [];
    LOG.debug(`Setup sprite animations: ${JSON.stringify(anims)} for: ${description.name}`);

    // Register all the animations of the sprite.
    anims.forEach(anim => {
      const config: GenerateFrameNamesConfig = {
        prefix: `${anim.name}/`,
        start: anim.from,
        end: anim.to,
        suffix: '.png'
      };
      const animationFrames = this.game.anims.generateFrameNames('test', config);
      this.game.anims.create({ defaultTextureKey: 'walk_down', frames: animationFrames, frameRate: 3, repeat: -1 });
    });
  }
}
