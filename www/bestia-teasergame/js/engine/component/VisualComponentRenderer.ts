import * as LOG from 'loglevel';

import { ComponentRenderer } from './ComponentRenderer';
import { Component } from 'entities/components/Component';
import { Entity } from 'entities';
import { VisualComponent, SpriteType } from 'entities/components/VisualComponent';
import { PositionComponent } from 'entities/components/PositionComponent';
import { MapHelper } from 'engine/map/MapHelper';
import { Point } from 'model';
import { ComponentType } from 'entities/components/ComponentType';

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
    const posComp = entity.getComponent(ComponentType.POSITION) as PositionComponent;
    if (!posComp) {
      return;
    }
    const position = posComp.position || new Point(0, 0);
    const px = MapHelper.pointToPixelCentered(position);

    const desc = this.getSpriteDescription(component);

    LOG.debug(`Entity: ${entity.id} Visual: ${component.id} (${component.sprite})`);

    const sprite = this.game.add.sprite(px.x, px.y, desc.name);
    entity.gameData[VisualComponentRenderer.DAT_SPRITE] = sprite;

    this.setupScaleAndOrigin(sprite, desc);
    this.setupSpriteAnimation(sprite, desc);

    this.setupMultiSprites(sprite, desc);
  }

  private getSpriteDescription(component: VisualComponent): SpriteDescription {
    const texture = this.game.textures.get(component.sprite);
    const desc = (texture.customData as any).meta.description;
    // If null fallback to the old format. Maybe changed in the future.
    if (!desc) {
      return this.game.cache.json.get(`${component.sprite}_desc`);
    } else {
      return desc;
    }
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
    if (!component.animation) {
      return;
    }
    const sprite = entity.gameData[VisualComponentRenderer.DAT_SPRITE] as Phaser.GameObjects.Sprite;
    const fullAnimationName = `${component.sprite}_${component.animation}`;
    LOG.debug(`Play animation: ${fullAnimationName} for entity: ${entity.id}`);
    sprite.anims.play(fullAnimationName, true);
    component.animation = null;
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
    const animsNames = anims.map(x => x.name);
    LOG.debug(`Setup sprite animations: ${animsNames} for: ${description.name}`);
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
        key: `${description.name}_${anim.name}`,
        frames: animationFrames,
        frameRate: anim.fps,
        repeat: -1
      };
      LOG.debug(JSON.stringify(animConfig));
      this.game.anims.create(animConfig);
    });
  }
}
