import * as LOG from 'loglevel';

import {
  Component, VisualComponent, SpriteType, PositionComponent, ComponentType
} from 'entities/components';
import { Entity } from 'entities';
import { Point } from 'model';
import { MapHelper } from 'map/MapHelper';

import { ComponentRenderer } from './ComponentRenderer';

export interface SpriteData {
  sprite: Phaser.GameObjects.Sprite;
  spriteName: string;
  lastPlayedAnimation?: string;
  childSprites: Array<{
    spriteName: string;
    sprite: Phaser.GameObjects.Sprite;
  }>;
}

interface SpriteAnimation {
  name: string;
  from: number;
  to: number;
  fps: number;
}

export interface SpriteDescription {
  name: string;
  type: SpriteType;
  version: number;
  scale: number;
  animations: SpriteAnimation[];
  anchor: Point;
  multiSprite: string[];
  collision?: number[][];
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

export function getSpriteDescriptionFromCache(
  spriteName: string,
  scene: Phaser.Scene
): SpriteDescription {
  return scene.cache.json.get(`${spriteName}_desc`);
}

function translateMovementToSubspriteAnimationName(moveAnimation: string): string {
  switch (moveAnimation) {
    case 'stand_down':
    case 'walk_down':
      return 'bottom';
    case 'stand_down_left':
    case 'walk_down_left':
      return 'bottom_left';
    case 'stand_down_right':
    case 'walk_down_right':
      return 'bottom_right';
    case 'stand_left':
    case 'walk_left':
      return 'left';
    case 'stand_right':
    case 'walk_right':
      return 'right';
    case 'stand_up_left':
    case 'walk_up_left':
      return 'top_left';
    case 'stand_up_right':
    case 'walk_up_right':
      return 'top_right';
    case 'stand_up':
    case 'walk_up':
      return 'top';
    default:
      return moveAnimation;
  }
}

export class VisualComponentRenderer extends ComponentRenderer<VisualComponent> {

  constructor(game: Phaser.Scene) {
    super(game);
  }

  get supportedComponent(): ComponentType {
    return ComponentType.VISUAL;
  }

  protected hasNotSetup(entity: Entity, component: VisualComponent): boolean {
    return !entity.data.visual;
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
      spriteName: component.sprite,
      childSprites: []
    };
    entity.data.visual = spriteData;

    this.setupScaleAndOrigin(sprite, desc);
    this.setupSpriteAnimation(sprite, desc);

    this.setupMultiSprites(spriteData, desc);
    this.updateChildSpriteOffset(spriteData);
  }

  private updateSpriteDepth(spriteData: SpriteData) {
    spriteData.sprite.depth = spriteData.sprite.y;
    spriteData.childSprites.forEach(s => {
      s.sprite.depth = spriteData.sprite.depth;
    });
  }

  private getSpriteDescription(component: VisualComponent): SpriteDescription {
    return getSpriteDescriptionFromCache(component.sprite, this.game);
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
      const offsetFileName = this.getOffsetFilename(multiSprite, spriteData.spriteName);
      const offsets = this.game.cache.json.get(offsetFileName) as SpriteOffsets;

      // Was not loaded. Should not happen.
      if (!msDesc || !offsets) {
        LOG.warn(`Subsprite description of offsets were not loaded. This should not happen: ${msDescName}`);
        return;
      }

      const msSprite = this.game.add.sprite(0, 0, multiSprite);
      // this.setupScaleAndOrigin(msSprite, msDesc);
      this.setupSpriteAnimation(msSprite, msDesc);

      // Das hier evtl besser auslagern?
      const defaultOffset = offsets.defaultCords || { x: 0, y: 0 };
      const defaultScale = offsets.scale || 1;
      const anchor = msDesc.anchor || {
        x: 0.5,
        y: 0.5
      };
      msSprite.setOrigin(anchor.x, anchor.y);
      msSprite.setScale(defaultScale);

      spriteData.childSprites.push({
        spriteName: multiSprite,
        sprite: msSprite
      });
    });
  }

  protected updateGameData(entity: Entity, component: VisualComponent) {
    const spriteData = entity.data.visual;

    if (!spriteData) {
      return;
    }

    if (component.animation) {
      spriteData.lastPlayedAnimation = component.animation;
      const fullAnimationName = `${component.sprite}_${component.animation}`;
      LOG.debug(`Play animation: ${fullAnimationName} for entity: ${entity.id}`);
      this.setSpriteAnimationName(spriteData.sprite, fullAnimationName);
      this.updateChildSpritesAnimation(spriteData, component.animation);
      component.animation = null;
    }

    if (component.oneshotAnimation) {
      this.setupOneshotAnimation(entity, component);
    }

    this.updateChildSpriteOffset(spriteData);
    this.updateSpriteDepth(spriteData);
  }

  private setupOneshotAnimation(entity: Entity, component: VisualComponent) {
    const spriteData = entity.data.visual;
    const fullAnimationName = `${component.sprite}_${component.oneshotAnimation}`;
    LOG.debug(`Play oneshot animation: ${fullAnimationName} for entity: ${entity.id}`);
    this.setSpriteAnimationName(spriteData.sprite, fullAnimationName);
    this.updateChildSpritesAnimation(spriteData, component.oneshotAnimation);

    const animationDuration = spriteData.sprite.anims.getTotalFrames() * spriteData.sprite.anims.msPerFrame;

    const previousAnimationName = spriteData.lastPlayedAnimation;
    this.game.time.addEvent({
      delay: animationDuration,
      callback: () => component.animation = previousAnimationName
    });
    component.oneshotAnimation = null;
  }

  private updateChildSpritesAnimation(spriteData: SpriteData, animationName: string) {
    spriteData.childSprites.forEach(childSprite => {
      const subspriteAnimationName = translateMovementToSubspriteAnimationName(animationName);
      const fullAnimationName = `${childSprite.spriteName}_${subspriteAnimationName}`;
      LOG.debug(`Play animation: ${fullAnimationName} for subsprite: ${childSprite.spriteName}`);
      this.setSpriteAnimationName(childSprite.sprite, fullAnimationName);
    });
  }

  private updateChildSpriteOffset(
    spriteData: SpriteData
  ) {
    spriteData.childSprites.forEach(childSprite => {
      const mainSpriteDesc = this.game.cache.json.get(`${spriteData.spriteName}_desc`) as SpriteDescription;
      const offsetFileName = this.getOffsetFilename(childSprite.spriteName, spriteData.spriteName);
      const offsets = this.game.cache.json.get(offsetFileName) as SpriteOffsets;
      const defaultOffset = offsets.defaultCords || { x: 0, y: 0 };
      const defaultScale = offsets.scale || 1;

      const x = spriteData.sprite.x + defaultOffset.x * mainSpriteDesc.scale;
      const y = spriteData.sprite.y + defaultOffset.y * mainSpriteDesc.scale;

      childSprite.sprite.setPosition(
        x,
        y,
      );
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
