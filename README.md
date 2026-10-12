# Maquis Caisse — Sprint 0 (socle)

Application Android native (Kotlin + Jetpack Compose + Room + Hilt),
caisse tactile pour maquis / buvettes / restaurants informels.
Offline-first, aucune dépendance réseau requise pour fonctionner.

## État actuel : Sprint 0 uniquement

Ce zip contient **uniquement le socle du projet** :
- Structure `data/ domain/ ui/ di/ navigation/ core/ common/`
  (le dossier `domain/` sera peuplé à partir du Sprint 1 — pas encore
  de use cases tant qu'il n'y a pas d'entité)
- Thème Compose (Material 3, sombre par défaut, forte lisibilité)
- Base Room vide (aucune entité — ajoutée au Sprint 1)
- Navigation entre écrans, tous en placeholder
- Injection de dépendances Hilt configurée

**Aucune fonctionnalité métier n'est encore implémentée** (pas de vente,
pas de produits, pas d'avoirs...). C'est volontaire : le prompt Cursor
fourni (`PROMPT_CURSOR_ANDROID.md`) impose de ne jamais coder plusieurs
sprints à la fois.

## ⚠️ Important — non testé/compilé

Ce projet a été généré dans un environnement sans SDK Android ni accès
au dépôt Maven de Google : il n'a **pas pu être compilé ni exécuté**
avant livraison. Ouvre-le dans Android Studio (Koala ou plus récent),
laisse Gradle synchroniser, et corrige les éventuels ajustements de
version mineurs (compileSdk, versions de dépendances) si Android Studio
en signale.

## Ouvrir le projet

1. Android Studio → **Open** → sélectionner le dossier `maquis-caisse/`
2. Laisser la synchronisation Gradle se faire (télécharge le wrapper si
   besoin, connexion Internet nécessaire à ce moment-là uniquement)
3. Lancer sur un émulateur ou un appareil (API 26+)

## Prochaine étape

Donner `PROMPT_CURSOR_ANDROID.md` à Cursor en lui demandant de continuer
à partir du **Sprint 1** (Produits avec image), en respectant la règle :
un sprint à la fois, validation avant de passer au suivant.
