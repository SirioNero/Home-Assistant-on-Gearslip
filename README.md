# Plancia

[English](#english) | [Italiano](#italiano)

## English

Home Assistant on your car screen, through [Gearslip](https://github.com/twinksoftware/Gearslip).

Plancia shows your real Home Assistant dashboards, full screen, exactly as they look in the browser or in the official app: same cards, same views, same theme. Home Assistant draws them itself; Plancia puts its frontend on the surface Gearslip lends to map apps and signs it in with your token, so it never asks you to log in.

The phone settings screen is available in nine languages; the in-car menu is in Italian for now.

### Getting around

- **The dashboard** fills the screen. Tap the cards as you would on the phone, and swipe to scroll.
- **The menu** slides in from the left: open it with Home Assistant’s own ☰ button at the top left, or with Plancia’s ☰ at the top right (Gearslip fades it out when you don’t touch the screen). It lists your dashboards in the same order as Home Assistant’s sidebar, including Energy, Map, Activity and History. Pick one and the menu closes on it. Tapping the dashboard beside the menu closes it too.
- **Plancia predefinita** (Default dashboard, in the menu) is the dashboard that opens when you get in the car. Until you pick one, it’s the one Home Assistant opens on its own.
- **Dimensione** (Size, in the menu) makes the dashboard bigger or smaller, from 60% to 150%: bigger reads better while driving, smaller fits more cards.
- **Ricarica la plancia** (Reload) reloads the page from scratch.
- **Back** closes a dialog or leaves a subview.

### Installation

1. From the [Releases](../../releases) page, download `Plancia-debug.apk` to your phone and install it. Android asks you to allow installing unknown apps; Play Protect may warn that the app is unknown.
2. Open Plancia on your phone, pick your language at the top (English, Italian, Spanish, German, French, Portuguese, Greek, Russian or Chinese; English until you choose) and enter:
   - your Home Assistant address at home, for example `http://homeassistant.local:8123`;
   - the address to use away from home (see below);
   - a long-lived access token (your Home Assistant user profile, Security tab).
   Tap “Salva e prova la connessione” (Save and test connection).
3. In the car, open Gearslip: Plancia appears among the apps.

To get updates automatically, add `https://github.com/SirioNero/Home-Assistant-on-Gearslip` to [Obtainium](https://github.com/ImranR98/Obtainium) and set the APK filter to `debug`. Each release is named after the app version (`v0.1.N`), so Obtainium can tell when an update is out.

`Plancia-release.apk` is identical, but only accepts the official Gearslip as the car screen: it’s the safer choice once you’ve checked that everything works. Both versions share the same signature, so you can switch between them without uninstalling.

### Away from home

In the car your phone is on mobile data, so the home address doesn’t respond. Plancia tries the home address first, then the remote one. You need one of these:

- Home Assistant Cloud (Nabu Casa): `https://<id>.ui.nabu.casa`;
- a VPN on the phone (WireGuard, Tailscale) and a Home Assistant address reachable through it.

Avoid a plain `http://` remote address without a VPN: your token would travel unencrypted.

### Troubleshooting

- **The screen stays dark or the page doesn’t load.** The dashboard runs in Android System WebView: update it from the Play Store (or your phone’s app store). If Home Assistant answers with an error, the message shows on the car screen; tap it to try again.
- **Gearslip says “host not accepted”.** The debug build accepts any host; the release build accepts only the official Gearslip. If you built Gearslip yourself, put its digest in `res/values/arrays.xml`.
- **Useful logs**: `adb logcat -s Plancia CarApp.Val`.

### Limitations

- It works on Gearslip. Official Android Auto doesn’t let a smart home app draw its own view: there, templates with a surface are reserved for navigation apps.
- Gearslip passes taps, swipes and flings, not raw touches: long presses and pinch zoom don’t reach the dashboard. Use **Dimensione** for the size.
- The car has no keyboard for the page, so text fields (search, Assist) can’t be typed into.
- The token stays in the app’s private preferences on the phone, and only goes to your Home Assistant pages: Plancia opens no other site.

### Building

Every push builds the app on GitHub Actions. On the `main` branch, if the `PLANCIA_KEYSTORE` secret (the signing key, base64-encoded) is set, it also publishes a release with both APKs; other branches and pull requests are built only as a check.

Locally: open the folder in Android Studio and press Run. Without `app/plancia.keystore`, the app is signed with your computer’s debug key.

### Contributing

Bug reports and pull requests are welcome, especially from people trying Plancia in their car with different Home Assistant setups. When you report a problem, include your car or head unit model, your Gearslip and Home Assistant versions, and what you see on the screen.

| File | What it contains |
|---|---|
| `data/HaClient.kt` | WebSocket connection to Home Assistant |
| `data/HaRepository.kt` | Which address answers (home or remote), dashboards, reconnection |
| `data/Panels.kt` | The dashboards in the order of Home Assistant’s sidebar |
| `car/web/DashboardDisplay.kt` | The Home Assistant frontend on the car surface: virtual display, WebView, sign-in, taps and scrolling |
| `car/web/PageScripts.kt` | The JavaScript added to the page: hidden sidebar, ☰ button, scrolling |
| `car/screens/` | The in-car screens: full-screen dashboard, menu, default dashboard, size |
| `phone/SettingsActivity.kt` | The settings screen on the phone |

## Italiano

Home Assistant sullo schermo dell’auto, tramite [Gearslip](https://github.com/twinksoftware/Gearslip).

Plancia mostra le tue plance di Home Assistant vere, a tutto schermo, esattamente come le vedi nel browser o nell’app ufficiale: stesse schede, stesse viste, stesso tema. Le disegna Home Assistant stesso: Plancia ne apre il frontend sulla superficie che Gearslip presta alle app con mappa e lo autentica con il tuo token, così non chiede mai il login.

### Come si naviga

- **La plancia** occupa tutto lo schermo. Si toccano le schede come sul telefono e si scorre col dito.
- **Il menu** compare da sinistra: si apre con il pulsante ☰ di Home Assistant, in alto a sinistra, oppure con il ☰ di Plancia in alto a destra (Gearslip lo sbiadisce quando non si tocca lo schermo). Elenca le plance nello stesso ordine della barra laterale di Home Assistant, comprese Energia, Mappa, Attività e Cronologia. Se ne sceglie una e il menu si chiude mostrandola. Anche un tocco sulla plancia accanto al menu lo chiude.
- **Plancia predefinita** (nel menu) è la plancia che si apre salendo in auto. Finché non la scegli è quella che Home Assistant apre da solo.
- **Dimensione** (nel menu) ingrandisce o rimpicciolisce la plancia, dal 60% al 150%: più grande si legge meglio alla guida, più piccola ci stanno più schede in una volta.
- **Ricarica la plancia** ricarica la pagina da capo.
- **Indietro** chiude un dialogo o esce da una sottovista.

### Installazione

1. Dalla pagina [Releases](../../releases) scarica `Plancia-debug.apk` sul telefono e installalo. Android chiede di consentire l’installazione di app sconosciute; Play Protect può avvisare che l’app non è nota.
2. Apri Plancia sul telefono, scegli la lingua in alto (inglese, italiano, spagnolo, tedesco, francese, portoghese, greco, russo o cinese; finché non scegli è in inglese) e inserisci:
   - l’indirizzo di casa di Home Assistant, per esempio `http://homeassistant.local:8123`;
   - l’indirizzo da fuori casa (vedi sotto);
   - un token di accesso a lungo termine (profilo utente in Home Assistant, scheda Sicurezza).
   Premi “Salva e prova la connessione”.
3. In auto apri Gearslip: Plancia compare tra le app.

Per ricevere gli aggiornamenti in automatico, aggiungi `https://github.com/SirioNero/Home-Assistant-on-Gearslip` a [Obtainium](https://github.com/ImranR98/Obtainium) e come filtro degli APK metti `debug`. Ogni release ha il nome della versione dell’app (`v0.1.N`), così Obtainium capisce quando c’è un aggiornamento.

`Plancia-release.apk` è identica ma accetta come schermo dell’auto solo Gearslip ufficiale: è la scelta più prudente una volta verificato che tutto funziona. Le due versioni hanno la stessa firma, quindi si può passare dall’una all’altra senza disinstallare.

### Fuori casa

In auto il telefono usa la rete mobile, quindi l’indirizzo di casa non risponde. Plancia prova prima quello di casa e poi quello remoto. Serve uno di questi:

- Home Assistant Cloud (Nabu Casa): `https://<id>.ui.nabu.casa`;
- una VPN sul telefono (WireGuard, Tailscale) e l’indirizzo di Home Assistant raggiungibile attraverso di essa.

Evita un indirizzo remoto in `http://` senza VPN: il token viaggerebbe in chiaro.

### Se qualcosa non va

- **Lo schermo resta scuro o la pagina non si carica.** La plancia gira in Android System WebView: aggiornala dal Play Store (o dallo store del tuo telefono). Se Home Assistant risponde con un errore, il messaggio compare sullo schermo dell’auto; toccalo per riprovare.
- **Gearslip dice “host not accepted”.** La build debug accetta qualsiasi host; la release solo Gearslip ufficiale. Con un Gearslip compilato da sé, il suo digest va in `res/values/arrays.xml`.
- **Log utili**: `adb logcat -s Plancia CarApp.Val`.

### Limiti

- Funziona su Gearslip. Android Auto ufficiale non permette a un’app domotica di disegnare la propria vista: lì i template con superficie sono riservati alle app di navigazione.
- Gearslip passa tocchi, scorrimenti e lanci, non i tocchi grezzi: la pressione prolungata e lo zoom con due dita non arrivano alla plancia. Per la dimensione c’è **Dimensione**.
- In auto non c’è una tastiera per la pagina, quindi nei campi di testo (ricerca, Assist) non si può scrivere.
- Il token resta nelle preferenze private dell’app sul telefono e va solo alle pagine del tuo Home Assistant: Plancia non apre altri siti.

### Compilare

Ogni push compila l’app su GitHub Actions. Sul ramo `main`, se è configurato il secret `PLANCIA_KEYSTORE` (la chiave di firma in base64), pubblica anche una release con i due APK; gli altri rami e le pull request vengono compilati solo per controllo.

In locale: apri la cartella in Android Studio e premi Run. Senza `app/plancia.keystore` l’app viene firmata con la chiave di debug del proprio computer.

### Contribuire

Segnalazioni e pull request sono benvenute, soprattutto da chi prova Plancia in auto con impianti Home Assistant diversi. Quando segnali un problema indica il modello di auto o di autoradio, la versione di Gearslip e di Home Assistant, e cosa vedi sullo schermo.

| File | Cosa contiene |
|---|---|
| `data/HaClient.kt` | Connessione WebSocket a Home Assistant |
| `data/HaRepository.kt` | Quale indirizzo risponde (casa o remoto), plance, riconnessione |
| `data/Panels.kt` | Le plance nell’ordine della barra laterale di Home Assistant |
| `car/web/DashboardDisplay.kt` | Il frontend di Home Assistant sulla superficie dell’auto: display virtuale, WebView, accesso, tocchi e scorrimento |
| `car/web/PageScripts.kt` | Il JavaScript aggiunto alla pagina: barra laterale nascosta, pulsante ☰, scorrimento |
| `car/screens/` | Le schermate in auto: plancia a tutto schermo, menu, plancia predefinita, dimensione |
| `phone/SettingsActivity.kt` | La schermata di configurazione sul telefono |
