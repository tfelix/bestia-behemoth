import * as LOG from 'loglevel';

import { ComponentRenderer } from './ComponentRenderer';
import { Component } from '../../entities/components/Component';
import { Entity } from '../../entities/Entity';
import { VisualComponent, SpriteType } from '../../entities/components/VisualComponent';
import { PositionComponent } from '../../entities/components/PositionComponent';
import { MapHelper } from '../map/MapHelper';
import { Point } from '../../entities/Point';
import { ComponentType } from '../../entities/components/ComponentType';

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

  protected hasNotSetup(entity: Entity, component: VisualComponent): boolean {
    return entity.gameData[VisualComponentRenderer.DAT_SPRITE] === undefined;
  }

  protected createGameData(entity: Entity, component: VisualComponent) {

    const texture = this.game.textures.get(component.sprite);
    const desc = (texture.customData as any).meta.description;

    LOG.debug(`Building: ${JSON.stringify(component)} (dynamic sprite)`);

    const posComp = entity.getComponent(ComponentType.POSITION) as PositionComponent;
    const position = posComp.position || { x: 0, y: 0 };
    const px = MapHelper.pointToPixelCentered(position);

    const sprite = this.game.add.sprite(px.x, px.y, desc.name);
    entity.gameData[VisualComponentRenderer.DAT_SPRITE] = sprite;

    this.setupScaleAndOrigin(sprite, desc);
    this.setupSpriteAnimation(sprite, desc);

    this.setupMultiSprites(sprite, desc);
  }

  private setupMultiSprites(
    sprite: Phaser.GameObjects.Sprite,
    desc: SpriteDescription
  ) {
    const multisprites = desc.multiSprite || [];

    multisprites.forEach(multiSprite => {
      LOG.debug(`Adding multisprite: ${multiSprite}`);

      // Get the desc file of the multisprite.
      const msDescName = `${multiSprite}_desc`;
      const msDesc = this.game.cache.json.get(msDescName) as SpriteDescription;

      // Was not loaded. Should not happen.
      if (msDesc === null) {
        LOG.warn(`Subsprite description was not loaded. This should not happen: ${msDescName}`);
        return;
      }

      const offsetFileName = this.getOffsetFilename(multiSprite, desc.name);
      const offsets = this.game.cache.json.get(offsetFileName) || {};

      const msSprite = this.game.add.sprite(0, 0, multiSprite);
      const defaultOffset = offsets.defaultCords || { x: 0, y: 0 };
      msSprite.setPosition(
        sprite.x + defaultOffset.x * desc.scale,
        sprite.y + defaultOffset.y * desc.scale
      );

      this.setupScaleAndOrigin(msSprite, msDesc);
      this.setupSpriteAnimation(msSprite, msDesc);
    });
  }

  protected updateGameData(entity: Entity, component: VisualComponent) {
    const sprite = entity.gameData[VisualComponentRenderer.DAT_SPRITE] as Phaser.GameObjects.Sprite;
    const animationName = component.animation || 'stand_down';
    sprite.anims.play(animationName, true);
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
    sprite.setOrigin(anchor.x, anchor.y);

    const scale = description.scale || 1;
    sprite.setScale(scale);
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
        zeroPad: 3,
        end: anim.to,
        suffix: '.png'
      };
      const animationFrames = this.game.anims.generateFrameNames(description.name, config);
      const animConfig = {
        key: anim.name,
        frames: animationFrames,
        frameRate: anim.fps,
        repeat: -1
      };
      LOG.debug(JSON.stringify(animConfig));
      this.game.anims.create(animConfig);
    });
  }
}
