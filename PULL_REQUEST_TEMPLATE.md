---
name: "PR: Fix ticket printing and number format"
about: "Propose fixes for ESC/POS printing and money formatting"
---

Cette PR applique plusieurs corrections visant à :

- Normaliser les montants envoyés à l'imprimante (remplacement des NBSP et conversion des chiffres fullwidth vers digits ASCII).
- Utiliser un encodage plus compatible (ISO-8859-1 puis fallback UTF-8) lors de l'envoi des lignes ESC/POS.
- Améliorer la robustesse de la connexion Bluetooth (delays et fallback vers socket insecure).
- Verrouiller l'UI pendant l'opération d'impression pour éviter les clics répétés.

Instructions de test :
- Vérifier l'impression sur une imprimante thermique Bluetooth configurée dans les paramètres.
- Vérifier l'affichage des montants (aucune apparition de glyphes CJK) et l'absence de besoin de plusieurs clics.
