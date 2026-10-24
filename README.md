# Plancia

Home Assistant sullo schermo dell’auto, tramite [Gearslip](https://github.com/twinksoftware/Gearslip).

A sinistra un elenco che Gearslip disegna con i suoi template: le plance di Home Assistant, le stanze, le etichette, poi le entità. A destra una vista disegnata da Plancia sulla superficie che Gearslip presta alle app con mappa: ogni luce accesa illumina la sua tessera del colore che ha davvero, le tapparelle mostrano le lamelle, gli anelli indicano luminosità e apertura, le stanze riassumono cosa c’è di acceso. Si tocca una tessera per comandarla, si scorre col dito.

## Come si naviga

- **Casa**: a sinistra le plance di Home Assistant, Stanze ed Etichette; a destra tutte le stanze.
- **Una plancia**: le sue viste diventano stanze da aprire, nello stesso ordine che hanno in Home Assistant. Le plance generate automaticamente diventano una vista per area.
- **Una stanza o una vista**: tutte le entità. Luci, prese, ventilatori e tapparelle si commutano con un tocco; scene e media player pure. Script, pulsanti e aspirapolvere chiedono conferma, così un tocco per sbaglio alla guida non fa partire niente. Serrature e allarmi sono in sola lettura.
- **Aggiorna** (in alto) rilegge plance, aree ed etichette.

## Installazione

1. Dalla pagina [Releases](../../releases) scarica `Plancia-debug.apk` sul telefono e installalo. Android chiede di consentire l’installazione di app sconosciute; Play Protect può avvisare che l’app non è nota.
2. Apri Plancia sul telefono e inserisci:
   - l’indirizzo di casa di Home Assistant, per esempio `http://homeassistant.local:8123`;
   - l’indirizzo da fuori casa (vedi sotto);
   - un token di accesso a lungo termine (profilo utente in Home Assistant, scheda Sicurezza).
   Premi “Salva e prova la connessione”.
3. In auto apri Gearslip: Plancia compare tra le app.

`Plancia-release.apk` è identica ma accetta come schermo dell’auto solo Gearslip ufficiale: è la scelta più prudente una volta verificato che tutto funziona. Le due versioni hanno la stessa firma, quindi si può passare dall’una all’altra senza disinstallare.

## Fuori casa

In auto il telefono usa la rete mobile, quindi l’indirizzo di casa non risponde. Plancia prova prima quello di casa e poi quello remoto. Serve uno di questi:

- Home Assistant Cloud (Nabu Casa): `https://<id>.ui.nabu.casa`;
- una VPN sul telefono (WireGuard, Tailscale) e l’indirizzo di Home Assistant raggiungibile attraverso di essa.

Evita un indirizzo remoto in `http://` senza VPN: il token viaggerebbe in chiaro.

## Se qualcosa non va

- **La colonna a sinistra c’è ma la parte destra resta vuota.** Gearslip potrebbe concedere la superficie solo ad alcune categorie di app. Nel manifest aggiungi `<category android:name="androidx.car.app.category.POI" />` accanto a quella IOT.
- **Gearslip dice “host not accepted”.** La build debug accetta qualsiasi host; la release solo Gearslip ufficiale. Con un Gearslip compilato da sé, il suo digest va in `res/values/arrays.xml`.
- **Log utili**: `adb logcat -s Plancia CarApp.Val`.

## Limiti

- Funziona su Gearslip. Android Auto ufficiale non permette a un’app domotica di disegnare la propria vista: lì i template con superficie sono riservati alle app di navigazione.
- Il token resta nelle preferenze private dell’app sul telefono.

## Compilare

Ogni push sul ramo `main` compila l’app su GitHub Actions e, se è configurato il secret `PLANCIA_KEYSTORE` (la chiave di firma in base64), pubblica una release con i due APK. Le pull request vengono compilate per controllo, senza firma e senza release.

In locale: apri la cartella in Android Studio e premi Run. Senza `app/plancia.keystore` l’app viene firmata con la chiave di debug del proprio computer.

## Contribuire

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
