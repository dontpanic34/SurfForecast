package com.surfcast.surfforecast

data class SurfSpotItem(
    val name: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    // Orientation de la plage : direction (degres, 0-359) vers laquelle elle regarde, c'est-a-dire
    // d'ou vient la houle qui la frappe de face (275 = plein ouest). Sert au score pour :
    //  - la direction de la houle (de face = 1, de travers = moins, a 90 degres = 0) ;
    //  - offshore / onshore : le vent d'onshore vient de la mer (meme direction), l'offshore de
    //    derriere la plage (direction opposee). Valeurs par defaut estimees, modifiables dans
    //    Parametres > Previsions. null = neutre (ancienne regle : plage orientee plein ouest).
    val idealSwellDirection: Int? = null
)

data class SurfSubRegionItem(val name: String, val spots: List<SurfSpotItem>)
data class SurfRegionItem(val name: String, val subRegions: List<SurfSubRegionItem>)
data class SurfCountryItem(val name: String, val regions: List<SurfRegionItem>)

object SurfDatabase {
    val countries = listOf(
        SurfCountryItem(
            name = "France",
            regions = listOf(
                SurfRegionItem(
                    name = "Nouvelle-Aquitaine",
                    subRegions = listOf(
                        SurfSubRegionItem("Gironde", listOf(
                            SurfSpotItem("Le Verdon", 45.5480, -1.0960, 285),
                            SurfSpotItem("Soulac", 45.5140, -1.1300, 275),
                            SurfSpotItem("L'Amélie", 45.4950, -1.1440, 275),
                            SurfSpotItem("Le Gurp", 45.4370, -1.1550, 275),
                            SurfSpotItem("Montalivet", 45.3800, -1.1590, 275),
                            SurfSpotItem("Hourtin", 45.1850, -1.1770, 275),
                            SurfSpotItem("Carcans", 45.0830, -1.1960, 275),
                            SurfSpotItem("Lacanau", 44.9980, -1.2050, 275),
                            SurfSpotItem("Le Porge", 44.8870, -1.2180, 275),
                            SurfSpotItem("La Jenny", 44.8350, -1.2290, 275),
                            SurfSpotItem("Le Grand Crohot", 44.7950, -1.2400, 275),
                            SurfSpotItem("Le Truc Vert", 44.7150, -1.2470, 275),
                            SurfSpotItem("Le Cap Ferret", 44.6280, -1.2480, 280),
                            SurfSpotItem("La Salie", 44.5200, -1.2510, 265)
                        )),
                        SurfSubRegionItem("Landes", listOf(
                            SurfSpotItem("Biscarrosse", 44.4480, -1.2540, 270),
                            SurfSpotItem("Mimizan", 44.2080, -1.2980, 270),
                            SurfSpotItem("Contis", 44.0930, -1.3250, 270),
                            SurfSpotItem("Vielle-Saint-Girons", 43.9530, -1.3650, 265),
                            SurfSpotItem("Moliets", 43.8540, -1.3930, 265),
                            SurfSpotItem("Messanges", 43.8160, -1.4050, 265),
                            SurfSpotItem("Hossegor", 43.6650, -1.4420, 260),
                            SurfSpotItem("Capbreton", 43.6420, -1.4450, 260),
                            SurfSpotItem("Seignosse", 43.6980, -1.4360, 262)
                        )),
                        SurfSubRegionItem("Pays Basque", listOf(
                            SurfSpotItem("Tarnos", 43.5350, -1.5170, 265),
                            SurfSpotItem("Boucau", 43.5280, -1.5220, 270),
                            SurfSpotItem("Anglet", 43.5040, -1.5360, 285),
                            SurfSpotItem("Biarritz", 43.4830, -1.5600, 290),
                            SurfSpotItem("Bidart", 43.4380, -1.5950, 290),
                            SurfSpotItem("Guéthary", 43.4240, -1.6110, 300),
                            SurfSpotItem("Saint-Jean-de-Luz", 43.4070, -1.6370, 310),
                            SurfSpotItem("Hendaye", 43.3730, -1.7740, 320)
                        ))
                    )
                ),
                SurfRegionItem(
                    name = "Bretagne & Vendée",
                    subRegions = listOf(
                        SurfSubRegionItem("Vendée", listOf(
                            SurfSpotItem("La Tranche-sur-Mer", 46.3400, -1.4380, 195),
                            SurfSpotItem("Longeville", 46.4170, -1.5030, 220),
                            SurfSpotItem("Jard-sur-Mer", 46.4120, -1.5790, 210),
                            SurfSpotItem("Les Sables-d'Olonne", 46.4830, -1.7680, 200),
                            SurfSpotItem("Saint-Gilles-Croix-de-Vie", 46.6970, -1.9510, 250)
                        )),
                        SurfSubRegionItem("Bretagne", listOf(
                            SurfSpotItem("La Torche", 47.8380, -4.3540, 260),
                            SurfSpotItem("Gâvres", 47.6910, -3.3510, 200),
                            SurfSpotItem("Quiberon", 47.5180, -3.1550, 250),
                            SurfSpotItem("Porspoder", 48.5080, -4.7690, 290),
                            SurfSpotItem("Morgat", 48.2250, -4.5020, 160)
                        ))
                    )
                )
            )
        ),
        SurfCountryItem(
            name = "Espagne",
            regions = listOf(
                SurfRegionItem(
                    name = "Côte Nord",
                    subRegions = listOf(
                        SurfSubRegionItem("Pays Basque Espagnol", listOf(
                            SurfSpotItem("Hondarribia", 43.3850, -1.7920, 330),
                            SurfSpotItem("San Sebastián", 43.3240, -1.9750, 350),
                            SurfSpotItem("Zarautz", 43.2870, -2.1640, 350),
                            SurfSpotItem("Mundaka", 43.4070, -2.6980, 350),
                            SurfSpotItem("Sopelana", 43.3850, -2.9970, 330)
                        )),
                        SurfSubRegionItem("Cantabrie & Asturies", listOf(
                            SurfSpotItem("Laredo", 43.4150, -3.4280, 10),
                            SurfSpotItem("Somo", 43.4560, -3.7430, 20),
                            SurfSpotItem("Santander", 43.4730, -3.7850, 30),
                            SurfSpotItem("Gijón", 43.5410, -5.6560, 0)
                        )),
                        SurfSubRegionItem("Galice", listOf(
                            SurfSpotItem("Ribadeo", 43.5510, -7.0390, 350),
                            SurfSpotItem("Pantín", 43.6390, -8.1130, 330),
                            SurfSpotItem("A Coruña", 43.3710, -8.4110, 320),
                            SurfSpotItem("Razo", 43.2920, -8.7060, 320)
                        ))
                    )
                )
            )
        ),
        SurfCountryItem(
            name = "Portugal",
            regions = listOf(
                SurfRegionItem(
                    name = "Façade Atlantique",
                    subRegions = listOf(
                        SurfSubRegionItem("Portugal Nord", listOf(
                            SurfSpotItem("Viana do Castelo", 41.6880, -8.8410, 270),
                            SurfSpotItem("Espinho", 41.0080, -8.6460, 270),
                            SurfSpotItem("Porto", 41.1610, -8.6870, 270)
                        )),
                        SurfSubRegionItem("Portugal Centre", listOf(
                            SurfSpotItem("Nazaré", 39.6050, -9.0770, 300),
                            SurfSpotItem("Peniche", 39.3550, -9.3780, 270),
                            SurfSpotItem("Ericeira", 38.9630, -9.4180, 290),
                            SurfSpotItem("Figueira da Foz", 40.1550, -8.8720, 270)
                        )),
                        SurfSubRegionItem("Lisbonne & Alentejo", listOf(
                            SurfSpotItem("Carcavelos", 38.6780, -9.3340, 190),
                            SurfSpotItem("Costa da Caparica", 38.6430, -9.2450, 260),
                            SurfSpotItem("Guincho", 38.7320, -9.4730, 280)
                        )),
                        SurfSubRegionItem("Algarve", listOf(
                            SurfSpotItem("Sagres", 37.0090, -8.9400, 230),
                            SurfSpotItem("Arrifana", 37.2990, -8.8710, 260),
                            SurfSpotItem("Carrapateira", 37.1920, -8.9030, 270)
                        ))
                    )
                )
            )
        )
    )

    fun getAllSpots(): List<SurfSpotItem> {
        return countries.flatMap { it.regions }
            .flatMap { it.subRegions }
            .flatMap { it.spots }
    }

    fun findSpotByName(name: String): SurfSpotItem? {
        return getAllSpots().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
