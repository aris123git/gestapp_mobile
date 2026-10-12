# Prompt Cursor — App Android « Caisse Maquis / Buvette » (v2, fusionnée)

## Rôle

Tu es un architecte logiciel Android senior, spécialisé Kotlin, Jetpack
Compose, Material 3, Room, Hilt, Coroutines, Flow/StateFlow, MVVM,
Clean Architecture, offline-first, optimisation des performances.

L'application doit rester fluide en usage intensif réel (maquis,
buvette, restaurant, bar, snack), y compris avec plusieurs milliers de
produits et plusieurs dizaines de milliers de ventes.

## Objectif

Créer une application Android native moderne pour maquis, buvettes et
restaurants. Ce n'est pas un portage de l'application desktop Python —
celle-ci sert uniquement de référence pour les règles métier (dettes,
stock, rapports). Toute l'architecture Android est pensée nativement.

## État du projet

**Le Sprint 0 est déjà livré** (structure du projet, thème Compose,
Room vide, Hilt, navigation avec écrans placeholder). Analyse-le avant
toute chose. Ne le recode pas. Étends-le.

## Principes fondamentaux (valables à chaque sprint)

- Toujours analyser le projet existant avant de modifier quoi que ce soit
- Ne jamais réécrire un composant fonctionnel — toujours étendre
- La logique métier ne se trouve jamais dans les écrans Compose
  (UI → ViewModel → UseCases → Repository → Room)
- Ne jamais coder plusieurs sprints simultanément — un sprint, validation,
  puis le suivant

## Architecture imposée

```
UI → ViewModel → UseCases → Repository → Room
```
Dossiers : `data/` `domain/` `ui/` `di/` `navigation/` `core/` `common/`

## Stack technique

Kotlin, Compose, Material 3, Hilt, Room, Coroutines, StateFlow, Coil,
Navigation Compose. Dépendances modernes et maintenues uniquement.

## Contraintes transverses

- Fonctionnement 100 % hors-ligne, aucune connexion requise
- Toute opération Room passe par `Dispatchers.IO`, jamais le Main Thread
- Migrations Room **toujours explicites**, jamais
  `fallbackToDestructiveMigration()` — conserver les données à chaque
  migration, avec tests de migration
- UX pensée pour un usage debout, rapide, à une main, parfois au soleil,
  parfois de nuit : boutons ≥ 48dp, animations courtes, contraste élevé,
  police lisible, retour visuel immédiat
- Performance : optimiser `LazyVerticalGrid`, `LazyColumn`, Coil, Room,
  éviter les recompositions Compose inutiles, ne jamais charger toutes
  les images simultanément

## Principe UX central — écran Caisse

1. Grille produits (photo, nom, prix), tuiles larges
2. Tap sur une tuile → pavé numérique en overlay
3. Saisie quantité → OK → ligne ajoutée au panier, overlay fermé
4. Répétition possible sans quitter l'écran
5. Panier visible en permanence (modification/suppression par
   long-press qui rouvre le pavé)
6. Bouton Valider → mode de paiement (espèces, Mobile Money, avoir,
   dette, mixte) → calcul monnaie → clôture

Le composant `NumericKeypad` doit être **générique et réutilisable** :
il servira aussi au paiement, à la remise, au remboursement, aux dettes
et aux avoirs plus tard. Le panier doit survivre aux rotations et
changements de configuration (`SavedStateHandle`).

---

## Sprint 1 — Produits avec image

- Entité `Product` (Room) : id, nom, catégorie, prix vente, prix achat,
  stock, seuil d'alerte, `image_path` (jamais une Uri Android brute),
  actif/inactif
- Upload image (galerie ou appareil photo), redimensionnement et
  compression automatiques avant sauvegarde en stockage privé
  (cible ~200-300 Ko)
- Système de remplacement d'image
- Grille produits avec Coil, fallback icône générique si pas d'image
- Première `Migration` Room (création de la table `products`)

## Sprint 2 — Caisse tactile (cœur de l'app)

- Implémenter le flux décrit ci-dessus intégralement
- Gestion des doublons dans le panier : additionner par défaut si le
  produit est déjà présent
- Persistance de la vente (`Sale` + `SaleItem`) à la validation
- Ticket simple (le partage/impression Bluetooth vient au Sprint 11)

## Sprint 3 — Gestion des tables

Indispensable pour un maquis/restaurant.
- Entité `Table` : numéro, nom, capacité, statut
  (`libre`, `occupée`, `réservée`, `à_nettoyer`)
- Changer de table, fusionner deux tables, séparer une facture

## Sprint 4 — Commandes ouvertes

Une commande peut rester ouverte plusieurs heures.
- Entité `Order` liée à une table, statuts : `ouverte`, `servie`,
  `payée`, `annulée`
- Reprendre une commande, ajouter/retirer des produits, transférer une
  commande vers une autre table

## Sprint 5 — Ouverture / fermeture de caisse

- Entité `CashSession` : montant initial à l'ouverture ; à la fermeture,
  détail ventes/espèces/Mobile Money/avoirs/dettes + écart de caisse
- **Impossible de vendre si aucune caisse n'est ouverte**

## Sprint 6 — Avoirs (monnaie ou boissons)

- Entité `Voucher` : id, client_id (nullable), type (`CASH`/`PRODUCT`),
  montant ou (product_id + quantité), date émission, date expiration
  (nullable), statut (`actif`, `partiellement_utilisé`, `soldé`,
  `expiré`, `annulé`), note
- Entité `VoucherRedemption` : historique d'utilisation, lié à la vente
  où l'avoir a été utilisé
- Utilisable comme mode de paiement dans l'écran de validation (Sprint 2)
- Règle à confirmer avec le commerçant avant de coder : que devient un
  avoir "boisson" si le produit est retiré du catalogue ?

## Sprint 7 — Dettes clients (« ardoise »)

Même principe que sur le desktop, pour cohérence :
- Entité `Debt` (client_id, sale_id nullable, montant_initial,
  montant_restant, échéance, statut, note)
- Entité `DebtPayment` (debt_id, montant, mode_paiement, date, note)
- Paiement partiel, historique, mise à jour automatique du solde client
- Utilisable comme mode de paiement dans l'écran caisse

## Sprint 8 — Stock et pertes

- Entrées/sorties de stock
- Motifs de perte normalisés (casse, vol, péremption, consommation
  interne, don, autre)
- Alerte stock faible sur l'écran d'accueil

## Sprint 9 — Utilisateurs et rôles

- Rôles : Admin, Manager, Caissier, Serveur — permissions par rôle

## Sprint 10 — Journal d'audit

- Entité `AuditLog` : ouverture/fermeture de caisse, annulation,
  remboursement, suppression, modification de prix, suppression produit

## Sprint 11 — Impression Bluetooth

- Abstraction `PrinterRepository`, jamais couplée directement à l'écran
  de caisse
- Implémentation ESC/POS Bluetooth dans un second temps

## Sprint 12 — Tableau de bord et statistiques

- Aujourd'hui / semaine / mois / année
- Meilleure boisson, meilleure catégorie, heure de pointe, ticket moyen,
  meilleur serveur
- Avoirs actifs en circulation (montant engagé, important pour la
  trésorerie), dettes clients en cours

## Sprint 13 — Sauvegarde

- Export automatique local, import d'une sauvegarde
- Ne rien coder côté cloud avant confirmation explicite du besoin

---

## Contraintes finales (après chaque sprint)

1. Vérifier que le projet compile
2. Vérifier qu'aucune fonctionnalité existante n'est cassée
3. Exécuter les tests
4. Lister les fichiers modifiés
5. Documenter les nouvelles classes
6. Proposer le message de commit Git correspondant

## Règle absolue

Ne jamais implémenter plusieurs sprints à la fois. Le Sprint 0 est déjà
fait. Commence par le **Sprint 1**, attends validation, puis continue.
