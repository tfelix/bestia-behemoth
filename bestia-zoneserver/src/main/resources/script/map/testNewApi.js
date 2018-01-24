
function main() {
    var entityId = 123;
    var entity = BAPI.getEntity(entityId);
    entity.set('visual.type', 'PACK');
    // BAPI.set(entityId, 'visual.type', 'PACK');
    // BAPI.set(entityId, 'visual.sprite', 'poring')
    entity.remove('visual');
    entity.takeDmg(123)
}
