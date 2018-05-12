import * as LOG from 'loglevel';

import { EntityStore } from '../entities/EntityStore';
import { Component } from '../entities/components/Component';
import { ComponentRenderer } from './component/ComponentRenderer';
import { VisualComponentRenderer } from './component/VisualComponentRenderer';
import { Entity } from '../entities/Entity';
import { ComponentType } from '../entities/components/ComponentType';
import { DebugComponentRenderer } from './component/DebugComponentRenderer';

export class EntityRenderer {

  private componentRenderer = new Map<ComponentType, ComponentRenderer<Component>>();

  constructor(
    private readonly game: Phaser.Scene,
    private readonly entityStore: EntityStore
  ) {
    this.addComponentRenderer(new VisualComponentRenderer(game));
    this.addComponentRenderer(new DebugComponentRenderer(game));
  }

  private addComponentRenderer(renderer: ComponentRenderer<Component>) {
    this.componentRenderer.set(renderer.supportedComponent, renderer);
  }

  public update() {
    this.updateNew();

    for (const e of this.entityStore.entities.values()) {
      for (const c of e.getComponentIterator()) {
        const renderer = this.componentRenderer.get(c.type);
        if (renderer) {
          renderer.render(e, c);
        }
      }
    }

    // this.entityStore.removedEntities.forEach(e => this.remove(e));
    // this.entityStore.removedEntities = [];
  }

  private updateNew() {
    if (this.entityStore.newEntities.length > 0) {
      LOG.debug(`Processing ${this.entityStore.newEntities.length} new entities.`);
    }
    this.entityStore.newEntities.forEach(entity => {
      for (const component of entity.getComponentIterator()) {
        LOG.debug(`Processing component: ${component.type}.`);
        const renderer = this.componentRenderer.get(component.type);
        if (renderer) {
          renderer.render(entity, component);
        }
      }
    });
    this.entityStore.newEntities = [];
  }
}
