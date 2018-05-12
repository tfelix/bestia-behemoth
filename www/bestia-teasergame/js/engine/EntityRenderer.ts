import * as LOG from 'loglevel';

import { EntityStore } from '../entities/EntityStore';
import { Component } from '../entities/components/Component';
import { ComponentRenderer } from './component/ComponentRenderer';
import { VisualComponentRenderer } from './component/VisualComponentRenderer';
import { Entity } from '../entities/Entity';
import { ComponentType } from '../entities/components/ComponentType';

export class EntityRenderer {

  private componentRenderer = new Map<ComponentType, ComponentRenderer<Component>>();

  constructor(
    private readonly game: Phaser.Scene,
    private readonly entityStore: EntityStore
  ) {
    this.addComponentRenderer(new VisualComponentRenderer(game));
  }

  private addComponentRenderer(renderer: ComponentRenderer<Component>) {
    this.componentRenderer.set(renderer.supportedComponent, renderer);
  }

  public update() {
    this.updateNew();

    this.entityStore.removedEntities.forEach(e => this.remove(e));
    this.entityStore.removedEntities = [];
  }

  private remove(entity: Entity) {

  }

  private updateNew() {
    this.entityStore.newEntities.forEach(entity => {

      for (const component of entity.getComponentIterator()) {
        const renderer = this.componentRenderer.get(component.type);
        if (renderer) {
          renderer.render(entity, component);
        }
      }
    });
    this.entityStore.newEntities = [];
  }
}
