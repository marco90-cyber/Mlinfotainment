# MLinfotainment

Launcher per l'auto, in stile Android Auto, per tutte le MLapps. Funziona in verticale, in orizzontale e sui tablet.
Kotlin + Jetpack Compose. Le impostazioni usano [MLTheme](https://github.com/marco90-cyber/MLTheme).

## Cosa fa

- **Schermata di guida** a moduli (si accendono, spengono e riordinano dalle impostazioni):
  navigazione, velocità GPS con orologio, musica, meteo, app preferite giganti
- **Navigatore** a scelta tra Google Maps, Waze e HERE WeGo (basta che sia installato): pulsante grande
  e scorciatoie Casa e Lavoro
- **Musica** di qualsiasi app: brano, copertina e comandi (serve l'accesso alle notifiche)
- **Meteo** da Open-Meteo (nessuna chiave)
- **Si apre da solo** quando guidi, con uno o più segnali: Bluetooth dell'auto, riconoscimento guida di Google,
  velocità GPS sopra una soglia. Ogni segnale basta da solo; c'è una pausa tra due aperture

## Primo avvio

Impostazioni > Permessi: attiva "Mostra sopra altre app" (apertura diretta), Bluetooth, riconoscimento attività,
posizione e "Batteria senza limiti". Poi scegli l'auto in Segnali di avvio > Dispositivi Bluetooth.

## Build

L'APK viene compilato da GitHub Actions a ogni push su `main` e pubblicato nelle **Releases**.
Per firmare con la tua chiave aggiungi i Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
