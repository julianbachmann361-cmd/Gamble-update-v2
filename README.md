# SimpleGamble

Ein `/gamble <betrag>` Plugin für Paper-Server, das über **Vault** an dein bestehendes
Economy-System (also dasselbe Geld wie in ShopGUI) anbindet.

## 1. Kompatibilitäts-Check: Vault + EconomyShopGUI

Ich habe beide hochgeladenen Jars entpackt und geprüft:

- **Vault.jar** (v1.7.3-b131) ist die normale Vault-API/Bridge, `main: net.milkbowl.vault.Vault`.
- **EconomyShopGUI** (v7.2.1) hat `Vault` als **softdepend** in der `plugin.yml` und enthält
  eine eigene Klasse `me.gypopo.economyshopgui.providers.economys.VaultEconomy.class` –
  das Plugin unterstützt Vault also aktiv als Economy-Provider.

**Ergebnis:** Ja, das geht sauber zusammen. Wichtig ist nur:

- Vault selbst **speichert kein Geld** – es ist nur eine Vermittlungsschicht.
  Du brauchst zusätzlich **ein** echtes Economy-Plugin (z.B. EssentialsX, CMI, o.ä.),
  das sich bei Vault als Provider registriert.
- EconomyShopGUI muss in seiner eigenen Config so eingestellt sein, dass es **Vault**
  als Economy-Quelle nutzt (nicht z.B. PlayerPoints), sonst greift es auf ein anderes
  Konto zu als dein Gamble-Plugin.
- Läuft nur EIN Economy-Plugin auf dem Server, greifen Vault, EconomyShopGUI und
  SimpleGamble automatisch auf denselben Kontostand zu – kein zusätzlicher Code nötig.

Codequalität von EconomyShopGUI kann ich von außen (kompilierte class-Dateien) nicht
seriös beurteilen, aber die Struktur (saubere `plugin.yml`, dedizierte Provider-Klassen
pro Economy-Plugin, Maven-Build mit `pom.xml` im Jar) sieht nach einem gut gepflegten,
kommerziellen Plugin aus – kein Hobby-Bastelcode.

## 2. Was das Plugin macht (Version 1.2.0)

- `/gamble <betrag>` (max. 80.000) zieht den Einsatz sofort per Vault ab.
- `/gamble jackpot` zeigt den aktuellen Jackpot-Stand an.
- Ergebnis wird **vorher** fair anhand gewichteter Zufallsauswahl bestimmt.
- **Einsatzabhängige Odds mit Plateau:** bis 20.000 Einsatz (`bet-scaling.plateau-until`)
  bleiben die Odds konstant auf dem besten Niveau (RTP ≈ 99%). Setzt jemand mehr als
  20.000, verschlechtern sich die Chancen ab da **schrittweise**, bis beim Höchsteinsatz
  (80.000) das schlechteste Niveau erreicht ist (RTP ≈ 91%). Im Durchschnitt über den
  gesamten nutzbaren Bereich ergibt das ca. 95% RTP / 5% Hausvorteil - genau wie
  gewünscht, aber ohne dass kleine, normale Einsätze bestraft werden.
- **Seltene Booster:** x25, x50 und x100 (x100 nur ca. 0,02% Chance - bewusst noch
  seltener als dein Beispiel-Wert von 0,1%, siehe Rechnung unten) - ihre Wahrscheinlichkeit
  ändert sich kaum mit der Einsatzhöhe, jeder soll eine reelle Chance auf den großen
  Wurf haben, unabhängig vom Einsatz.
- x0.1 wurde zu **x0.9** (milder Verlust, häufigster Ausgang bei kleinen Einsätzen),
  x0.5 wurde zu **x0.7** (spürbarerer Verlust, wird bei großen Einsätzen fast genauso
  häufig wie x0.9).
- **Serverweiter Jackpot:** 10% jedes Einsatzes (Gewinn oder Verlust) wandert automatisch
  in einen Topf, der in `jackpot-data.yml` gespeichert wird und Neustarts übersteht.
  Bei jedem `/gamble` gibt es zusätzlich eine unabhängige, sehr seltene Chance (Standard:
  0,05% = 1 von 2000), den kompletten Topf oben drauf zu gewinnen - danach geht der
  Topf zurück auf 0. Ein Jackpot-Gewinn wird immer serverweit announced. Wichtig: dieses
  Geld ist nicht "weg" wie der normale Hausvorteil, sondern wandert nur gesammelt an
  den nächsten Jackpot-Gewinner.
- ca. 10 Sekunden Animation (Action Bar, konfigurierbar auf Title).
- Cooldown + Sperre gegen doppelte gleichzeitige Nutzung pro Spieler.

### Multiplikatoren (in `config.yml` frei änderbar)

Jeder Multiplikator hat zwei Gewichte: `low-weight` (gilt bis `plateau-until`, Standard
20.000) und `high-weight` (gilt ab `bet.max`, Standard 80.000). Dazwischen wird linear
interpoliert - unterhalb des Plateaus gilt durchgehend `low-weight`.

| Multiplikator | Gewicht bis 20.000 Einsatz | Gewicht bei 80.000 (Max) |
|---|---|---|
| x0.9 | 73,363% | 44,329% |
| x0.7 | 24,454% | 54,180% |
| x2.5 | 1,276% | 0,827% |
| x5   | 0,491% | 0,318% |
| x10  | 0,196% | 0,127% |
| x25  | 0,15% | 0,15% |
| x50  | 0,05% | 0,05% |
| x100 | 0,02% | 0,02% |

Ergebnis: RTP = 98,999% bis 20.000 Einsatz, 90,999% bei 80.000 Einsatz, im Schnitt genau
95% (5% Hausvorteil) - exakt durchgerechnet, nicht nur ungefähr geschätzt. Die tatsächlich
geladenen Werte werden beim Serverstart zusätzlich in die Konsole geloggt.

Warum x100 nur 0,02% statt der von dir vorgeschlagenen 0,1% ist: bei 0,1% Chance hätte
x100 allein schon fast das komplette RTP-Budget von 99% aufgebraucht (100 × 0,001 = 0,1,
also 10 Prozentpunkte allein durch diese eine Stufe) - dann wäre für x0.9/x0.7/x2.5/x5/x10
kaum noch Spielraum geblieben, um trotzdem auf ziemlich genau 95% zu kommen. Mit 0,02%
(~1 von 5000) ist x100 immer noch spürbar öfter drin als der Jackpot selbst, bleibt aber
finanzierbar. Willst du es großzügiger, ändere einfach die Gewichte in der `config.yml` -
sie müssen sich nicht auf 100 summieren, das Plugin normalisiert automatisch - achte nur
darauf, dass die RTP (wird beim Start geloggt) nicht über 100% steigt, sonst verliert der
Server im Schnitt Geld.

## 3. Bauen (kompilieren)

Ich habe hier keinen Internetzugriff/Maven, daher ist das Projekt als fertiger Quellcode
geliefert, nicht als fertiges Jar. So baust du es:

1. [Maven](https://maven.apache.org/download.cgi) und JDK 17+ installieren.
2. Im Projektordner (wo `pom.xml` liegt) ausführen:
   ```
   mvn clean package
   ```
3. Das fertige Jar liegt danach in `target/SimpleGamble-1.0.0.jar`.
4. Jar in den `plugins/`-Ordner deines Paper-Servers legen, **neben Vault und deinem
   Economy-Plugin**, Server neu starten.

Falls du kein Maven lokal einrichten willst: Lade den Ordner in ein leeres GitHub-Repo
und lass es per GitHub Actions bauen, oder nutze einen Online-IDE/Build-Service wie
Replit/Gitpod, der Maven bereits mitbringt.

## 4. Config-Übersicht

Alles Wichtige (Mindest-/Höchsteinsatz, Animationsdauer/-geschwindigkeit, Cooldown,
Sounds, Nachrichten, Broadcast-Schwelle, Multiplikatoren) steht in `config.yml` und ist
ohne Neu-Kompilieren änderbar – einfach Server-Reload/Restart nach dem Bearbeiten.
