# Plancia

[English](#english) | [Italiano](#italiano)

## English

Home Assistant on your car screen, through [Gearslip](https://github.com/twinksoftware/Gearslip).

On the left, a list that Gearslip draws with its own templates: your Home Assistant dashboards, rooms and labels, then the entities. On the right, a view that Plancia draws itself on the surface Gearslip lends to map apps: every light that is on lights up its tile in its real color, covers show their slats, rings show brightness and position, and each room sums up what is on. Tap a tile to control it, swipe to scroll.

The app’s interface is in Italian for now.

### Getting around

- **Home**: dashboards, Rooms and Labels on the left; all rooms on the right.
- **A dashboard**: its views become rooms you can open, in the same order as in Home Assistant. Auto-generated dashboards become one view per area.
- **A room or a view**: all its entities. Lights, plugs, fans and covers toggle with a tap, and so do scenes and media players. Scripts, buttons and vacuums ask for confirmation, so an accidental tap while driving doesn’t start anything. Locks and alarms are read-only.
- **Aggiorna** (Refresh, at the top) reloads dashboards, areas and labels.

### Installation

1. From the [Releases](../../releases) page, download `Plancia-debug.apk` to your phone and install it. Android asks you to allow installing unknown apps; Play Protect may warn that the app is unknown.
2. Open Plancia on your phone and enter:
   - your Home Assistant address at home, for example `http://homeassistant.local:8123`;
   - the address to use away from home (see below);
   - a long-lived access token (your Home Assistant user profile, Security tab).
   Tap “Salva e prova la connessione” (Save and test connection).
3. In the car, open Gearslip: Plancia appears among the apps.

`Plancia-release.apk` is identical, but only accepts the official Gearslip as the car screen: it’s the safer choice once you’ve checked that everything works. Both versions share the same signature, so you can switch between them without uninstalling.

### Away from home

In the car your phone is on mobile data, so the home address doesn’t respond. Plancia tries the home address first, then the remote one. You need one of these:

- Home Assistant Cloud (Nabu Casa): `https://<id>.ui.nabu.casa`;
- a VPN on the phone (WireGuard, Tailscale) and a Home Assistant address reachable through it.

Avoid a plain `http://` remote address without a VPN: your token would travel unencrypted.

### Troubleshooting

- **The list on the left shows up, but the right side stays empty.** Gearslip may lend the surface only to some app categories. In the manifest, add `<category android:name="androidx.car.app.category.POI" />` next to the IOT one.
- **Gearslip says “host not accepted”.** The debug build accepts any host; the release build accepts only the official Gearslip. If you built Gearslip yourself, put its digest in `res/values/arrays.xml`.
- **Useful logs**: `adb logcat -s Plancia CarApp.Val`.

### Limitations

- It works on Gearslip. Official Android Auto doesn’t let a smart home app draw its own view: there, templates with a surface are reserved for navigation apps.
- The token stays in the app’s private preferences on the phone.

### Building

Every push to the `main` branch builds the app on GitHub Actions and, if the `PLANCIA_KEYSTORE` secret (the signing key, base64-encoded) is set, publishes a release with both APKs. Pull requests are built as a check, unsigned and without a release.

Locally: open the folder in Android Studio and press Run. Without `app/plancia.keystore`, the app is signed with your computer’s debug key.

### Contributing

Bug reports and pull requests are welcome, especially from people trying Plancia in their car with different Home Assistant setups. When you report a problem, include your car or head unit model, your Gearslip and Home Assistant versions, and what you see on the screen.

| File | What it contains |
|---|---|
| `data/HaClient.kt` | WebSocket connection to Home Assistant |
| `data/HaRepository.kt` | States, areas, labels, dashboards, reconnection |
| `data/Lovelace.kt` | Reading the views of a dashboard |
| `logic/Entities.kt` | Italian texts, commands, what to show and in which order |
| `car/render/TilePainter.kt` | How tiles look |
| `car/render/Colors.kt`, `Palette.kt` | Colors and day/night themes |
| `car/render/SurfaceRenderer.kt` | Grid, scrolling, taps on the surface |
| `car/screens/` | The in-car screens |
| `phone/SettingsActivity.kt` | The settings screen on the phone |

## Italiano

Home Assistant sullo schermo dell’auto, tramite [Gearslip](https://github.com/twinksoftware/Gearslip).

A sinistra un elenco che Gearslip disegna con i suoi template: le plance di Home Assistant, le stanze, le etichette, poi le entità. A destra una vista disegnata da Plancia sulla superficie che Gearslip presta alle app con mappa: ogni luce accesa illumina la sua tessera del colore che ha davvero, le tapparelle mostrano le lamelle, gli anelli indicano luminosità e apertura, le stanze riassumono cosa c’è di acceso. Si tocca una tessera per comandarla, si scorre col dito.

### Come si naviga

- **Casa**: a sinistra le plance di Home Assistant, Stanze ed Etichette; a destra tutte le stanze.
- **Una plancia**: le sue viste diventano stanze da aprire, nello stesso ordine che hanno in Home Assistant. Le plance generate automaticamente diventano una vista per area.
- **Una stanza o una vista**: tutte le entità. Luci, prese, ventilatori e tapparelle si commutano con un tocco; scene e media player pure. Script, pulsanti e aspirapolvere chiedono conferma, così un tocco per sbaglio alla guida non fa partire niente. Serrature e allarmi sono in sola lettura.
- **Aggiorna** (in alto) rilegge plance, aree ed etichette.

### Installazione

1. Dalla pagina [Releases](../../releases) scarica `Plancia-debug.apk` sul telefono e installalo. Android chiede di consentire l’installazione di app sconosciute; Play Protect può avvisare che l’app non è nota.
2. Apri Plancia sul telefono e inserisci:
   - l’indirizzo di casa di Home Assistant, per esempio `http://homeassistant.local:8123`;
   - l’indirizzo da fuori casa (vedi sotto);
   - un token di accesso a lungo termine (profilo utente in Home Assistant, scheda Sicurezza).
   Premi “Salva e prova la connessione”.
3. In auto apri Gearslip: Plancia compare tra le app.

`Plancia-release.apk` è identica ma accetta come schermo dell’auto solo Gearslip ufficiale: è la scelta più prudente una volta verificato che tutto funziona. Le due versioni hanno la stessa firma, quindi si può passare dall’una all’altra senza disinstallare.

### Fuori casa

In auto il telefono usa la rete mobile, quindi l’indirizzo di casa non risponde. Plancia prova prima quello di casa e poi quello remoto. Serve uno di questi:

- Home Assistant Cloud (Nabu Casa): `https://<id>.ui.nabu.casa`;
- una VPN sul telefono (WireGuard, Tailscale) e l’indirizzo di Home Assistant raggiungibile attraverso di essa.

Evita un indirizzo remoto in `http://` senza VPN: il token viaggerebbe in chiaro.

### Se qualcosa non va

- **La colonna a sinistra c’è ma la parte destra resta vuota.** Gearslip potrebbe concedere la superficie solo ad alcune categorie di app. Nel manifest aggiungi `<category android:name="androidx.car.app.category.POI" />` accanto a quella IOT.
- **Gearslip dice “host not accepted”.** La build debug accetta qualsiasi host; la release solo Gearslip ufficiale. Con un Gearslip compilato da sé, il suo digest va in `res/values/arrays.xml`.
- **Log utili**: `adb logcat -s Plancia CarApp.Val`.

### Limiti

- Funziona su Gearslip. Android Auto ufficiale non permette a un’app domotica di disegnare la propria vista: lì i template con superficie sono riservati alle app di navigazione.
- Il token resta nelle preferenze private dell’app sul telefono.

### Compilare

Ogni push sul ramo `main` compila l’app su GitHub Actions e, se è configurato il secret `PLANCIA_KEYSTORE` (la chiave di firma in base64), pubblica una release con i due APK. Le pull request vengono compilate per controllo, senza firma e senza release.

In locale: apri la cartella in Android Studio e premi Run. Senza `app/plancia.keystore` l’app viene firmata con la chiave di debug del proprio computer.

### Contribuire

Segnalazioni e pull request sono benvenute, soprattutto da chi prova Plancia in auto con impianti Home Assistant diversi. Quando segnali un problema indica il modello di auto o di autoradio, la versione di Gearslip e di Home Assistant, e cosa vedi sullo schermo.

| File | Cosa contiene |
|---|---|
| `data/HaClient.kt` | Connessione WebSocket a Home Assistant |
| `data/HaRepository.kt` | Stati, aree, etichette, plance, riconnessione |
| `data/Lovelace.kt` | Lettura delle viste di una plancia |
| `logic/Entities.kt` | Testi in italiano, comandi, cosa mostrare e in che ordine |
| `car/render/TilePainter.kt` | Aspetto delle tessere |
| `car/render/Colors.kt`, `Palette.kt` | Colori e temi giorno/notte |
| `car/render/SurfaceRenderer.kt` | Griglia, scorrimento, tocchi sulla superficie |
| `car/screens/` | Le schermate in auto |
| `phone/SettingsActivity.kt` | La schermata di configurazione sul telefono |
