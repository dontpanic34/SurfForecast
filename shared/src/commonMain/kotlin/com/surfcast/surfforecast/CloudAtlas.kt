package com.surfcast.surfforecast

// Généré par le script d'atlas (voir docs/DECISIONS.md) : rectangles des sprites dans l'atlas net (sky_atlas_nuages.webp, taille d'origine,
// réduit de moitié pour charger vite) et dans l'atlas flou de la brume (sky_atlas_flou.webp, taille d'origine).
internal object CloudAtlas {
    /** x, y, largeur, hauteur dans l'atlas net ; x, y, largeur, hauteur dans l'atlas flou. */
    val rects: List<IntArray> = listOf(
        intArrayOf(0, 0, 1158, 462, 0, 0, 1158, 462),
        intArrayOf(1160, 0, 641, 420, 1162, 0, 641, 420),
        intArrayOf(0, 464, 506, 410, 1807, 0, 506, 410),
        intArrayOf(508, 464, 574, 241, 2317, 0, 574, 241),
        intArrayOf(1084, 464, 308, 133, 2895, 0, 308, 133),
        intArrayOf(1394, 464, 277, 118, 3207, 0, 277, 118),
        intArrayOf(1673, 464, 231, 123, 3488, 0, 231, 123),
        intArrayOf(1906, 464, 142, 100, 3723, 0, 142, 100),
        intArrayOf(0, 876, 118, 100, 3869, 0, 118, 100),
        intArrayOf(120, 876, 120, 86, 0, 466, 120, 86),
        intArrayOf(242, 876, 121, 93, 124, 466, 121, 93),
        intArrayOf(366, 876, 149, 60, 249, 466, 149, 60)
    )
    /** Aire d'origine (photo non agrandie) : classe les sprites en grands, moyens et petits. */
    val sprites: List<CloudSprite> = listOf(
        CloudSprite(0, 2316, 924, 311492f),
        CloudSprite(1, 1283, 840, 118865f),
        CloudSprite(2, 1012, 821, 107937f),
        CloudSprite(3, 1148, 482, 67614f),
        CloudSprite(4, 616, 267, 24657f),
        CloudSprite(5, 554, 237, 16862f),
        CloudSprite(6, 462, 246, 11942f),
        CloudSprite(7, 284, 201, 6353f),
        CloudSprite(8, 237, 201, 4743f),
        CloudSprite(9, 240, 172, 4741f),
        CloudSprite(10, 243, 187, 4266f),
        CloudSprite(11, 299, 121, 4183f)
    )
}
