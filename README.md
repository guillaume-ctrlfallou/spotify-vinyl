# Vinyle — lecteur Spotify pour Android

Quand Spotify joue, l'appli affiche un vinyle avec la pochette qui tourne, sur un fond de brume
qui pulse aux couleurs de la pochette. Toucher le vinyle met en pause (il s'arrête) ou relance.
Quand le téléphone est verrouillé, le lecteur s'affiche par-dessus l'écran de verrouillage.

## Installer sur le téléphone

1. Sur le téléphone, ouvre la page **Releases** du dépôt GitHub et télécharge `app-debug.apk`.
2. Ouvre le fichier. Android demande d'autoriser l'installation depuis le navigateur : accepte.
   Si « Play Protect » prévient que l'appli est inconnue, choisis « Installer quand même ».
3. Ouvre l'appli **Vinyle** et suis les boutons :
   - **Accès aux notifications** : active « Vinyle » (c'est ce qui permet de lire ce que joue Spotify).
     Sur Android 13+, si l'interrupteur est grisé : Paramètres > Applis > Vinyle > menu ⋮ en haut à droite >
     « Autoriser les paramètres restreints », puis recommence.
   - **Notifications plein écran** (Android 14+) : active « Vinyle ».
   - Accepte les notifications.
4. Lance une musique sur Spotify, verrouille le téléphone, rallume l'écran : le vinyle est là.

Astuce : si le lecteur n'apparaît plus après un moment, désactive l'optimisation de batterie pour
« Vinyle » (Paramètres > Applis > Vinyle > Batterie > Non restreinte).

## Mettre à jour
Chaque modification poussée sur `main` refabrique l'APK automatiquement (onglet Actions) et remplace
celui de la release « latest ». Il suffit de réinstaller par-dessus.
