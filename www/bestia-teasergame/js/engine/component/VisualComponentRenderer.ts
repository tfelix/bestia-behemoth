import * as LOG from 'loglevel';

import { ComponentRenderer } from "./ComponentRenderer";
import { ComponentType, Component } from "../../entities/components/Component";
import { Entity } from "../../entities/Entity";
import { VisualComponent, SpriteType } from '../../entities/components/VisualComponent';
import { PositionComponent } from '../../entities/components/PositionComponent';
import { MapHelper } from '../map/MapHelper';
import { Point } from '../../entities/Point';

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
  anchor: Point
}

export class VisualComponentRenderer extends ComponentRenderer<VisualComponent> {

  constructor(game: Phaser.Scene) {
    super(game);
  }

  get supportedComponent(): ComponentType {
    return ComponentType.VISUAL;
  }

  protected createGameData(entity: Entity, component: VisualComponent) {

    const texture = this.game.textures.get(component.sprite);
    const desc = (texture.customData as any).meta.description;

    LOG.debug('Building: ' + JSON.stringify(component) + ' (dynamic sprite)');

    const posComp = entity.getComponent(ComponentType.POSITION) as PositionComponent;
    const px = MapHelper.pointToPixel(posComp.position);
    const sprite = this.game.add.sprite(px.x, px.y, desc.name);
    
    // groups.get(GROUP_LAYERS.SPRITES).add(sprite);

    this.setupSpriteAnimation(sprite, desc);

    // Add the multi sprites if there are some of them.
    var multisprites = desc.multiSprite || [];

    /*
		multisprites.forEach(function (msName) {
			LOG.debug('Adding multisprite to main sprite: ' + msName);

			// Get the desc file of the multisprite.
			var msDescName = msName + '_desc';
			var msDesc = this._game.cache.getJSON(msDescName);

			// Generate offset information.
			let offsetFileName = this._getOffsetFilename(msName, desc.name);
			let offsets = this._game.cache.getJSON(offsetFileName) || {};

			// Was not loaded. Should not happen.
			if (msDesc == null) {
				LOG.warn('Subsprite description was not loaded. This should not happen: ' + msDescName);
				return;
			}

			let msSprite = this._game.make.sprite(0, 0, msName);

			let defaultCords = offsets.defaultCords || {
				x: 0,
				y: 0
			};
			msSprite.x = defaultCords.x;
			msSprite.y = defaultCords.y;

			// Setup the normal data.
			// setupSpriteAnimation(msSprite, msDesc);

			// addSubsprite(sprite, msSprite);

    }, this);*/
    
    // Das hier auslagern in eine eigene Component Anzeige.
    sprite.setInteractive();
    sprite.setPosition(0, 0);
    sprite.setOrigin(0.5, 1);
    const text = this.game.add.text(0, 0, 'rocket');
    text.visible = false;
    text.setOrigin(0.5, 0)
    const container = this.game.add.container(100, 100, [sprite, text]);
    sprite.on('mouseover', this.hoverHandler, this);
  }

  private hoverHandler(entitySprite) {
    alert('geht');
  }

  protected updateGameData(entity: Entity, component: VisualComponent) {
    LOG.debug('Updating not implemented.');
  }

  private setupScaleAndOrigin(sprite: Phaser.GameObjects.Sprite, description: SpriteDescription) {
    // Setup the normal data.
    const anchor = description.anchor || {
      x: 0.5,
      y: 0.5
    };
    sprite.displayOriginX = anchor.x
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
    LOG.debug('Setup sprite animations:' + JSON.stringify(anims) + ' for: ' + description.name);

    // Register all the animations of the sprite.
    anims.forEach(anim => {
      const config: GenerateFrameNamesConfig = {
        prefix: anim.name + '/',
        start: anim.from,
        end: anim.to,
        suffix: '.png'
      };
      const animationFrames = this.game.anims.generateFrameNames('test', config);
      this.game.anims.create({defaultTextureKey: 'walk_down', frames: animationFrames, frameRate: 3, repeat: -1});
    });
  }
}