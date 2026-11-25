# Vinyle — lecteur Spotify pour Android

Fond d'écran animé : quand Spotify joue, la pochette devient un vinyle rond qui tourne, sur un fond
de rubans lumineux façon MilkDrop aux couleurs de la pochette. Il s'affiche derrière l'horloge de
l'écran de verrouillage : on déverrouille normalement. Sur l'écran d'accueil, toucher le vinyle met
en pause ou relance.

## Installer sur le téléphone

1. Sur le téléphone, ouvre la page **Releases** du dépôt GitHub et télécharge `app-debug.apk`.
2. Ouvre le fichier. Android demande d'autoriser l'installation depuis le navigateur : accepte.
   Si « Play Protect » prévient que l'appli est inconnue, choisis « Installer quand même ».
3. Ouvre l'appli **Vinyle** et suis les boutons :
   - **Accès aux notifications** : active « Vinyle » (c'est ce qui permet de lire ce que joue Spotify).
     Sur Android 13+, si l'interrupteur est grisé : Paramètres > Applis > Vinyle > menu ⋮ en haut à droite >
     « Autoriser les paramètres restreints », puis recommence.
   - **Choisir le fond d'écran** : applique-le à l'écran de verrouillage (ou aux deux écrans).
4. Lance une musique sur Spotify, verrouille le téléphone, rallume l'écran : le vinyle est là.

Astuce : si le lecteur n'apparaît plus après un moment, désactive l'optimisation de batterie pour
« Vinyle » (Paramètres > Applis > Vinyle > Batterie > Non restreinte).

## Mettre à jour
Chaque modification poussée sur `main` refabrique l'APK automatiquement (onglet Actions) et remplace
celui de la release « latest ». Il suffit de réinstaller par-dessus.
