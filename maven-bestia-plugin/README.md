# Bestia-Maven-Plugin

Dies ist ein Helper Plugin welches dazu eingesetzt werden kann die Bestia Game Ressourcen zu überprüfen und ggf. Fehler schon im Buildprozess zu erkennen. Folgende goals werden implementiert.

## Konfiguration

Die Variable assetRootDir muss in der POM gesetzt werden. Sie gibt den Ort des Wurzelverzeichnisses der Game Assets an.

## validate-maps - Validieren der Map

Dabei werden alle Maps auf ihre korrekten Angaben überprüft. Folgende Eigenschaften werden überprüft:

1. Sind alle Layer vorhanden und haben den richtigen Namen? 
  * spawn
  * portals
  * scripts
  * layer_n Durchgängig numeriert? Von -n bis +m.

2. Ist der Spawn der Mobs richtig angegeben?

   Namen der Spawn Layer Einträge: id:n
   n muss in den Map-Eigenschaften vorhanden sein.
   Eintrag in den Map-Eigenschaften für jeden Spawn: ID,MOB_DB_NAME,COUNT,MIN_DELAY-MAX_DELAY z.B.:1,poring,0,0-10

## validate-references - Validieren der Referenzen (TODO)

Vor allem Asset Packs besitzen interne URLs und verweise auf weitere Dateien die heruntergladen werden müssen. Diese und auch die Maps benötigen oft weitere Daten welche in den Game Assets vorhanden sein müssen. Diese referenzierten Dateien werden durch das Goal auf Vorhandensein überprüft.

1. mob: Alle im Asset Pack beschriebenen ATLAS, Sprites, etc. vorhanden?
2. map: Sind alle Tilemaps zugreifbar die von der Map referenziert werden? Sind alle Sounds/Assets vorhanden?

## convert-tmx - Erzeugt aus den TMX maps neue JSON maps.

Der Server verwendet TMX Daten da diese leichter zu handhaben sind als die JSON Dateien. Daher sind dies auch die master-files die es zu ändern gilt. JSON Maps werden automatisch ausgehend von den TMX maps neu erstellt durch dieses goal. Sie werden im gleichen Verzeichnis abgelegt wie die TMX map.

## TODO

Sind für alle Mobs in der Datenbank alle notwendigen Daten im Asset vorhanden?
Asset Packs vorhanden?


Konvertieren der Map
Kombinieren der Multi-Layer in eine gemeinsame Tilemap und Anpassung der entsprechenden Tile Spritesheets. Ausgabe der Datei in den dafür vorgesehenen Speicherort.

Überprüfen der Mob Sprites
Alle Animationen gesetzt?
Sind alle in den Asset Pack beschriebenen Dateien vorhanden?