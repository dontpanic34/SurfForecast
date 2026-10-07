package com.surfcast.surfforecast

/**
 * Sauvegarde du journal de bord dans un fichier (version web : le journal vit dans le
 * navigateur, un fichier évite de le perdre si on vide les données du site).
 * [import] ouvre le sélecteur de fichier et rappelle avec true si la sauvegarde a été restaurée.
 */
class SessionLogBackup(
    val export: () -> Unit,
    val import: (onDone: (Boolean) -> Unit) -> Unit
)
