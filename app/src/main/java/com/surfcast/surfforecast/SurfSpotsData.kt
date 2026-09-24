package com.surfcast.surfforecast

data class SurfSpotItem(
    val name: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    // Point 3 : direction (degres, 0-359) d'ou vient la houle qui frappe ce spot de face.
    // null tant que le spot n'a pas ete renseigne individuellement -> traite comme neutre
    // (Coeff_Direction = 1.0) par SurfScoring.kt, jamais penalise par defaut.
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
                            SurfSpotItem("Le Verdon", 45.5480, -1.0960),
                            SurfSpotItem("Soulac", 45.5140, -1.1300),
                            SurfSpotItem("L'Amélie", 45.4950, -1.1440),
                            SurfSpotItem("Le Gurp", 45.4370, -1.1550),
                            SurfSpotItem("Montalivet", 45.3800, -1.1590),
                            SurfSpotItem("Hourtin", 45.1850, -1.1770),
                            SurfSpotItem("Carcans", 45.0830, -1.1960),
                            SurfSpotItem("Lacanau", 44.9980, -1.2050),
                            SurfSpotItem("Le Porge", 44.8870, -1.2180),
                            SurfSpotItem("La Jenny", 44.8350, -1.2290),
                            SurfSpotItem("Le Grand Crohot", 44.7950, -1.2400),
                            SurfSpotItem("Le Truc Vert", 44.7150, -1.2470),
                            SurfSpotItem("Le Cap Ferret", 44.6280, -1.2480),
                            SurfSpotItem("La Salie", 44.5200, -1.2510)
                        )),
                        SurfSubRegionItem("Landes", listOf(
                            SurfSpotItem("Biscarrosse", 44.4480, -1.2540),
                            SurfSpotItem("Mimizan", 44.2080, -1.2980),
                            SurfSpotItem("Contis", 44.0930, -1.3250),
                            SurfSpotItem("Vielle-Saint-Girons", 43.9530, -1.3650),
                            SurfSpotItem("Moliets", 43.8540, -1.3930),
                            SurfSpotItem("Messanges", 43.8160, -1.4050),
                            SurfSpotItem("Hossegor", 43.6650, -1.4420),
                            SurfSpotItem("Capbreton", 43.6420, -1.4450),
                            SurfSpotItem("Seignosse", 43.6980, -1.4360)
                        )),
                        SurfSubRegionItem("Pays Basque", listOf(
                            SurfSpotItem("Tarnos", 43.5350, -1.5170),
                            SurfSpotItem("Boucau", 43.5280, -1.5220),
                            SurfSpotItem("Anglet", 43.5040, -1.5360),
                            SurfSpotItem("Biarritz", 43.4830, -1.5600),
                            SurfSpotItem("Bidart", 43.4380, -1.5950),
                            SurfSpotItem("Guéthary", 43.4240, -1.6110),
                            SurfSpotItem("Saint-Jean-de-Luz", 43.4070, -1.6370),
                            SurfSpotItem("Hendaye", 43.3730, -1.7740)
                        ))
                    )
                ),
                SurfRegionItem(
                    name = "Bretagne & Vendée",
                    subRegions = listOf(
                        SurfSubRegionItem("Vendée", listOf(
                            SurfSpotItem("La Tranche-sur-Mer", 46.3400, -1.4380),
                            SurfSpotItem("Longeville", 46.4170, -1.5030),
                            SurfSpotItem("Jard-sur-Mer", 46.4120, -1.5790),
                            SurfSpotItem("Les Sables-d'Olonne", 46.4830, -1.7680),
                            SurfSpotItem("Saint-Gilles-Croix-de-Vie", 46.6970, -1.9510)
                        )),
                        SurfSubRegionItem("Bretagne", listOf(
                            SurfSpotItem("La Torche", 47.8380, -4.3540),
                            SurfSpotItem("Gâvres", 47.6910, -3.3510),
                            SurfSpotItem("Quiberon", 47.5180, -3.1550),
                            SurfSpotItem("Porspoder", 48.5080, -4.7690),
                            SurfSpotItem("Morgat", 48.2250, -4.5020)
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
                            SurfSpotItem("Hondarribia", 43.3850, -1.7920),
                            SurfSpotItem("San Sebastián", 43.3240, -1.9750),
                            SurfSpotItem("Zarautz", 43.2870, -2.1640),
                            SurfSpotItem("Mundaka", 43.4070, -2.6980),
                            SurfSpotItem("Sopelana", 43.3850, -2.9970)
                        )),
                        SurfSubRegionItem("Cantabrie & Asturies", listOf(
                            SurfSpotItem("Laredo", 43.4150, -3.4280),
                            SurfSpotItem("Somo", 43.4560, -3.7430),
                            SurfSpotItem("Santander", 43.4730, -3.7850),
                            SurfSpotItem("Gijón", 43.5410, -5.6560)
                        )),
                        SurfSubRegionItem("Galice", listOf(
                            SurfSpotItem("Ribadeo", 43.5510, -7.0390),
                            SurfSpotItem("Pantín", 43.6390, -8.1130),
                            SurfSpotItem("A Coruña", 43.3710, -8.4110),
                            SurfSpotItem("Razo", 43.2920, -8.7060)
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
                            SurfSpotItem("Viana do Castelo", 41.6880, -8.8410),
                            SurfSpotItem("Espinho", 41.0080, -8.6460),
                            SurfSpotItem("Porto", 41.1610, -8.6870)
                        )),
                        SurfSubRegionItem("Portugal Centre", listOf(
                            SurfSpotItem("Nazaré", 39.6050, -9.0770),
                            SurfSpotItem("Peniche", 39.3550, -9.3780),
                            SurfSpotItem("Ericeira", 38.9630, -9.4180),
                            SurfSpotItem("Figueira da Foz", 40.1550, -8.8720)
                        )),
                        SurfSubRegionItem("Lisbonne & Alentejo", listOf(
                            SurfSpotItem("Carcavelos", 38.6780, -9.3340),
                            SurfSpotItem("Costa da Caparica", 38.6430, -9.2450),
                            SurfSpotItem("Guincho", 38.7320, -9.4730)
                        )),
                        SurfSubRegionItem("Algarve", listOf(
                            SurfSpotItem("Sagres", 37.0090, -8.9400),
                            SurfSpotItem("Arrifana", 37.2990, -8.8710),
                            SurfSpotItem("Carrapateira", 37.1920, -8.9030)
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
