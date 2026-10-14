package com.maquis.caisse.core

object SettingsKeys {
    const val SHOP_NAME = "shop_name"
    const val SHOP_ADDRESS = "shop_address"
    const val SHOP_PHONE = "shop_phone"
    const val TICKET_FOOTER = "ticket_footer"
    const val PRINT_ENABLED = "print_enabled"
    // "true" (défaut) = imprimer le ticket à l'encaissement ; "false" = ne pas imprimer.
    // Choix fait par le vendeur dans la fenêtre de paiement (n'a d'effet que si PRINT_ENABLED = "true").
    const val PRINT_ON_PAYMENT = "print_on_payment"
    const val PRINT_WIDTH = "print_width" // 58 | 80
    const val PRINTER_ADDRESS = "printer_address"
    const val PRINTER_NAME = "printer_name"
    // Page de codes ESC/POS envoyée à l'init (ESC t n).
    // "0"  = PC437 (défaut, imprimantes génériques occidentales)
    // "16" = WPC1252 (Windows-1252)
    // "-1" = aucune commande de page de codes (legacy / imprimantes sans ESC t)
    const val PRINTER_CODEPAGE = "printer_codepage"
    // "1" (défaut) = ticket envoyé comme image (accents corrects) ; "0" = texte ESC/POS.
    const val PRINT_AS_IMAGE = "print_as_image"
    // "true" (défaut) = suivi de stock actif ; "false" = aucun stock géré (ni blocage, ni décrément).
    const val STOCK_TRACKING_ENABLED = "stock_tracking_enabled"
    // "true" (défaut) = bouton « Vente libre » en caisse.
    const val FREE_ENTRY_ENABLED = "free_entry_enabled"
}
