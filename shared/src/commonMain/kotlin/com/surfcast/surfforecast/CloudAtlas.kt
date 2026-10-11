package com.surfcast.surfforecast

// Généré par le script d'atlas (voir docs/DECISIONS.md) : rectangles des sprites dans l'atlas net (sky_atlas_nuages.webp, x2 par
// rapport aux photos d'origine) et dans l'atlas flou de la brume (sky_atlas_flou.webp, taille d'origine).
internal object CloudAtlas {
    /** x, y, largeur, hauteur dans l'atlas net ; x, y, largeur, hauteur dans l'atlas flou. */
    val rects: List<IntArray> = listOf(
        intArrayOf(0, 0, 2316, 924, 0, 0, 1158, 462),
        intArrayOf(2320, 0, 1283, 840, 1162, 0, 641, 420),
        intArrayOf(0, 928, 1012, 821, 1807, 0, 506, 410),
        intArrayOf(1016, 928, 1148, 482, 2317, 0, 574, 241),
        intArrayOf(2168, 928, 616, 267, 2895, 0, 308, 133),
        intArrayOf(2788, 928, 554, 237, 3207, 0, 277, 118),
        intArrayOf(3346, 928, 462, 246, 3488, 0, 231, 123),
        intArrayOf(3812, 928, 284, 201, 3723, 0, 142, 100),
        intArrayOf(0, 1753, 237, 201, 3869, 0, 118, 100),
        intArrayOf(241, 1753, 240, 172, 0, 466, 120, 86),
        intArrayOf(485, 1753, 243, 187, 124, 466, 121, 93),
        intArrayOf(732, 1753, 299, 121, 249, 466, 149, 60)
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
