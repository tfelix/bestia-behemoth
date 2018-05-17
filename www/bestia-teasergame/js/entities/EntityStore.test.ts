import * as mocha from 'mocha';
import * as chai from 'chai';
import chaiRx from 'chai-rx';

import { EntityStore } from './EntityStore';
import { Entity } from './Entity';

const expect = chai.expect;
describe('My math library', () => {

  const store = new EntityStore();

  it('should be able to add things correctly' , () => {
    const entity = new Entity(1);
    store.addEntity(entity);
    expect(1).to.eq(1);
  });

});
