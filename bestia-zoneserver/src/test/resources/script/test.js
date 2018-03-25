print('Hello World');

function main() {
Bestia.entity('mob/blob')
  .position(10, 20)
  .script()
    .setCallbackOnce('onEffectExec', 1000)
    .setLivetime(12000)
    .setInterval(1000, 'onTouch');
}