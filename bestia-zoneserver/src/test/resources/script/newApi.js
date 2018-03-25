function onTouch() {
  Bestia.info('Geht')
}

function main() {
Bestia.entity('mob/blob')
  .position(10, 20);
  .script()
    .setCallbackOnce('onEffectExec', 1000)
    .setLivetime(entityId, 12000);
    .setInterval(entityId, 1000, 'onTouch');
}
