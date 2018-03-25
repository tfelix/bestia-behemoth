function main() {
Bestia.entity('mob/blob').script()
  .setCallbackOnce('myCallback', 1000)
  .setLivetime(50000)
  .setInterval('myInterval', 1000)
}
