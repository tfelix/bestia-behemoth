import * as LOG from 'loglevel';

import { ComponentRenderer } from './ComponentRenderer';
import { Component } from 'entities/components/Component';
import { Entity } from 'entities';
import { VisualComponent, SpriteType } from 'entities/components/VisualComponent';
import { PositionComponent } from 'entities/components/PositionComponent';
import { MapHelper } from 'engine/map/MapHelper';
import { Point } from 'model';
import { ComponentType } from 'entities/components/ComponentType';

export interface SpriteData {
  sprite: Phaser.GameObjects.Sprite;
  name: string;
  childSprites: Array<{
    name: string;
    sprite: Phaser.GameObjects.Sprite;
  }>;
}

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

interface SpriteOffsets {
  targetSprite: string;
  scale: number;
  defaultCords: {
    x: number;
    y: number;
  };
  offsets: Array<{
    name: string;
    triggered: string;
    offsets: Array<{
      x: number;
      y: number;
    }>;
  }>;
}

function translateMovementToSubspriteAnimationName(moveAnimation: string): string {
  switch (moveAnimation) {
    case 'stand_down':
    case 'walk_down':
      return 'bottom';
    case 'stand_down_left':
    case 'walk_down_left':
    case 'stand_down_right':
    case 'walk_down_right':
      return 'bottom_left';
    case 'stand_left':
    case 'walk_left':
    case 'stand_right':
    case 'walk_right':
      return 'left';
    case 'stand_up_left':
    case 'walk_up_left':
    case 'stand_up_right':
    case 'walk_up_right':
      return 'top_left';
    case 'stand_up':
    case 'walk_up':
      return 'top';
    default:
      return moveAnimation;
  }
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
    const spriteData: SpriteData = {
      sprite: sprite,
      name: component.sprite,
      childSprites: []
    };
    entity.gameData[VisualComponentRenderer.DAT_SPRITE] = spriteData;

    this.setupScaleAndOrigin(sprite, desc);
    this.setupSpriteAnimation(sprite, desc);

    this.setupMultiSprites(spriteData, desc);
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
    spriteData: SpriteData,
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

      const msSprite = this.game.add.sprite(0, 0, multiSprite);
      this.setupScaleAndOrigin(msSprite, msDesc);
      this.setupSpriteAnimation(msSprite, msDesc);
      this.updateChildSpriteOffset(desc.name, msDesc.name, spriteData.sprite, msSprite);

      spriteData.childSprites.push({
        name: multiSprite,
        sprite: msSprite
      });
    });
  }

  private updateChildSpriteOffset(
    parentSpriteName: string,
    childSpriteName: string,
    parentSprite: Phaser.GameObjects.Sprite,
    childSprite: Phaser.GameObjects.Sprite
  ) {
    const offsetFileName = this.getOffsetFilename(childSpriteName, parentSpriteName);
    const offsets = this.game.cache.json.get(offsetFileName) as SpriteOffsets;

    const defaultOffset = offsets.defaultCords || { x: 0, y: 0 };
    const defaultScale = offsets.scale || 1;
    childSprite.setPosition(
      parentSprite.x + defaultOffset.x * defaultScale,
      parentSprite.y + defaultOffset.y * defaultScale
    );
  }

  protected updateGameData(entity: Entity, component: VisualComponent) {
    if (!component.animation) {
      return;
    }
    const spriteData = entity.gameData[VisualComponentRenderer.DAT_SPRITE] as SpriteData;
    const fullAnimationName = `${component.sprite}_${component.animation}`;
    LOG.debug(`Play animation: ${fullAnimationName} for entity: ${entity.id}`);
    this.setSpriteAnimationName(spriteData.sprite, fullAnimationName);
    this.updateChildSprites(spriteData, component.animation);
    component.animation = null;
  }

  private updateChildSprites(spriteData: SpriteData, animationName: string) {
    spriteData.childSprites.forEach(childSprite => {

      const subspriteAnimationName = translateMovementToSubspriteAnimationName(animationName);
      const fullAnimationName = `${childSprite.name}_${subspriteAnimationName}`;
      LOG.debug(`Play animation: ${fullAnimationName} for subsprite: ${childSprite.name}`);
      this.setSpriteAnimationName(childSprite.sprite, fullAnimationName);

      this.updateChildSpriteOffset(spriteData.name, childSprite.name, spriteData.sprite, childSprite.sprite);
    });
  }

  private setSpriteAnimationName(sprite: Phaser.GameObjects.Sprite, animation: string) {
    if (this.needsMirror(animation)) {
      sprite.flipX = true;
      const correctedAnimationName = animation.replace('_right', '_left');
      sprite.anims.play(correctedAnimationName, true);
    } else {
      sprite.flipX = false;
      sprite.anims.play(animation, true);
    }
  }

  private needsMirror(animationName: string): boolean {
    return animationName.endsWith('right');
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
    const animsNames = anims.map(x => `${description.name}_${x.name}`);
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
      this.game.anims.create(animConfig);
    });
  }
}
