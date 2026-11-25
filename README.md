# 🎶 Vinyle

**Un lecteur Spotify en vinyle pour l'écran de verrouillage Android.**
La pochette de l'album devient un disque qui tourne, sur un fond de rubans lumineux façon MilkDrop
qui pulsent aux couleurs de la pochette. *(English below.)*

[![Build APK](https://github.com/guillaume-ctrlfallou/spotify-vinyl/actions/workflows/build.yml/badge.svg)](https://github.com/guillaume-ctrlfallou/spotify-vinyl/actions/workflows/build.yml)
![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
![License: MIT](https://img.shields.io/badge/license-MIT-blue)

**[⬇️ Télécharger l'APK](https://github.com/guillaume-ctrlfallou/spotify-vinyl/releases/download/latest/app-debug.apk)**

---

## ✨ Fonctionnalités

- **Vinyle vivant** : la pochette entière découpée en disque, avec sillons et reflet. Il tourne pendant la lecture et ralentit jusqu'à l'arrêt en pause.
- **Fond MilkDrop** : rubans de lumière animés (shader AGSL) dans les couleurs extraites de la pochette, qui battent au rythme et se calment en pause.
- **Intégré à l'écran de verrouillage** : toucher le disque = pause / lecture, flèches précédent / suivant sur les côtés, titre et barre de durée. L'écran de verrouillage d'origine reste actif : **empreinte, horloge et déverrouillage fonctionnent normalement**.
- **Lecteur dans le volet des notifications** au même style (disque, rubans, couleurs de la pochette), pleine largeur.
- **Écran de réglages** qui montre l'état de chaque autorisation (✓ / ✗), un bouton « Tester » et un journal des derniers événements.

## 📲 Installation

1. Sur le téléphone, ouvre le lien **Télécharger l'APK** ci-dessus et installe-le (autorise l'installation depuis le navigateur ; si Play Protect prévient, « Installer quand même »).
2. Lance une musique sur Spotify puis ouvre **Vinyle**. Passe chaque ligne au vert ✓ :
   - **Accès aux notifications** : c'est ce qui permet de lire ce que joue Spotify (rien d'autre n'est lu).
   - **Notifications**, **Afficher par-dessus les autres applis**, **Notifications plein écran**, **Batterie non restreinte**.
   - **Mode intégré** (Accessibilité > Applis installées > *Vinyle — écran de verrouillage*) : c'est lui qui pose les boutons sur l'écran de verrouillage sans le cacher.
   - **Fond d'écran** : choisis Vinyle pour **les deux écrans** (accueil et verrouillage).
3. Verrouille le téléphone et rallume l'écran 🎉

### Samsung : si une autorisation est grisée ou refusée
Android bloque certains réglages pour les applis installées hors Play Store.
- Paramètres > Applications > **Vinyle** > menu **⋮** > **Autoriser les paramètres restreints** (le menu apparaît après un premier refus).
- Si ce menu n'existe pas : Paramètres > Sécurité et confidentialité > **Blocage automatique** (Auto Blocker), désactive-le le temps de l'étape précédente.
- Pour ne pas avoir deux lecteurs sur l'écran de verrouillage, retire le lecteur multimédia de la **Now Bar** dans les réglages de l'écran de verrouillage.

## ⚙️ Comment ça marche

| Pièce | Rôle |
|---|---|
| `MediaListener` | Service d'écoute des notifications : récupère la session média de Spotify (titre, pochette, état, contrôles) et extrait les couleurs avec Palette. |
| `VinylWallpaper` | Fond d'écran animé : shader AGSL des rubans + disque dessiné sur un Canvas matériel. |
| `LockOverlayService` | Service d'accessibilité : petites fenêtres posées *au-dessus* de l'écran de verrouillage (zone du disque, flèches, infos). Ne lit pas le contenu de l'écran. |
| `ShadePlayer` / `ShadeArt` | Notification personnalisée du volet (images fixes du disque et des rubans). |
| `LockActivity` | Lecteur plein écran de secours, utilisé seulement si le mode intégré est désactivé. |

## ⚠️ Limites connues

- Testé sur Samsung (One UI) uniquement. Android 13 minimum (shaders AGSL).
- Les rubans suivent un rythme régulier, pas le vrai son.
- Dans le volet, Android n'autorise que des images fixes : pas d'animation.
- `targetSdk` est volontairement à 30 : c'est ce qui permet une notification personnalisée pleine largeur.
- L'APK est signé avec une clé de debug publique (`app/debug.keystore`), pratique pour un projet perso. Pour distribuer ta propre version, signe-la avec ta clé.

## 🛠️ Compiler

```bash
./gradlew assembleDebug   # JDK 17 + Android SDK
```
Chaque push sur `main` compile l'APK via GitHub Actions et met à jour la release `latest`.

## 🤝 Contribuer

Les idées et les PR sont bienvenues : paroles synchronisées, rubans qui réagissent au son, autres lecteurs que Spotify, support d'autres marques… Ouvre une issue pour en discuter.

## 📄 Licence

[MIT](LICENSE). Projet non affilié à Spotify.

---

## 🇬🇧 English

**Vinyle** turns your Android lock screen into a vinyl player for Spotify: the album cover spins as a record over MilkDrop-style light ribbons tinted with the cover's colors.

- Tap the record to play/pause, edge arrows for previous/next, title and progress, while the stock lock screen (fingerprint, clock, unlock) keeps working.
- Matching full-width player in the notification shade.
- Setup screen with live permission status, a test button and an event log.

**Install:** grab the [APK](https://github.com/guillaume-ctrlfallou/spotify-vinyl/releases/download/latest/app-debug.apk), open Vinyle and turn every line green (notification access, overlay, full-screen notifications, battery, accessibility "integrated mode", live wallpaper on both screens). On Samsung, use *App info > ⋮ > Allow restricted settings* (and temporarily disable Auto Blocker) if a toggle is greyed out.

Android 13+, tested on Samsung One UI. MIT licensed. Not affiliated with Spotify.
