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
                            SurfSpotItem("Soulac", 45.5140, -1.1300, 305),
                            SurfSpotItem("L'Amélie", 45.4950, -1.1440, 295),
                            SurfSpotItem("Le Gurp", 45.4370, -1.1550, 280),
                            SurfSpotItem("Montalivet", 45.3800, -1.1590, 270),
                            SurfSpotItem("Hourtin", 45.1850, -1.1770, 275),
                            SurfSpotItem("Carcans", 45.0830, -1.1960, 275),
                            SurfSpotItem("Lacanau", 44.9980, -1.2050, 275),
                            SurfSpotItem("Le Porge", 44.8870, -1.2180, 275),
                            SurfSpotItem("La Jenny", 44.8350, -1.2290, 280),
                            SurfSpotItem("Le Grand Crohot", 44.7950, -1.2400, 280),
                            SurfSpotItem("Le Truc Vert", 44.7150, -1.2470, 275),
                            SurfSpotItem("Le Cap Ferret", 44.6280, -1.2480, 275),
                            SurfSpotItem("La Salie", 44.5200, -1.2510, 260)
                        )),
                        SurfSubRegionItem("Landes", listOf(
                            SurfSpotItem("Biscarrosse", 44.4480, -1.2540, 275),
                            SurfSpotItem("Mimizan", 44.2080, -1.2980, 270),
                            SurfSpotItem("Contis", 44.0930, -1.3250, 270),
                            SurfSpotItem("Vielle-Saint-Girons", 43.9530, -1.3650, 280),
                            SurfSpotItem("Moliets", 43.8540, -1.3930, 290),
                            SurfSpotItem("Messanges", 43.8160, -1.4050, 280),
                            SurfSpotItem("Hossegor", 43.6650, -1.4420, 260),
                            SurfSpotItem("Capbreton", 43.6420, -1.4450, 300),
                            SurfSpotItem("Seignosse", 43.6980, -1.4360, 270)
                        )),
                        SurfSubRegionItem("Pays Basque", listOf(
                            SurfSpotItem("Tarnos", 43.5350, -1.5170, 265),
                            SurfSpotItem("Boucau", 43.5280, -1.5220, 270),
                            SurfSpotItem("Anglet", 43.5040, -1.5360, 300),
                            SurfSpotItem("Biarritz", 43.4830, -1.5600, 315),
                            SurfSpotItem("Bidart", 43.4380, -1.5950, 290),
                            SurfSpotItem("Guéthary", 43.4240, -1.6110, 325),
                            SurfSpotItem("Saint-Jean-de-Luz", 43.4070, -1.6370, 325),
                            SurfSpotItem("Hendaye", 43.3730, -1.7740, 340)
                        ))
                    )
                ),
                SurfRegionItem(
                    name = "Bretagne & Vendée",
                    subRegions = listOf(
                        SurfSubRegionItem("Vendée", listOf(
                            SurfSpotItem("La Tranche-sur-Mer", 46.3400, -1.4380, 170),
                            SurfSpotItem("Longeville", 46.4170, -1.5030, 220),
                            SurfSpotItem("Jard-sur-Mer", 46.4120, -1.5790, 210),
                            SurfSpotItem("Les Sables-d'Olonne", 46.4830, -1.7680, 200),
                            SurfSpotItem("Saint-Gilles-Croix-de-Vie", 46.6970, -1.9510, 250)
                        )),
                        SurfSubRegionItem("Bretagne", listOf(
                            SurfSpotItem("La Torche", 47.8380, -4.3540, 260),
                            SurfSpotItem("Gâvres", 47.6910, -3.3510, 200),
                            SurfSpotItem("Quiberon", 47.5180, -3.1550, 260),
                            SurfSpotItem("Porspoder", 48.5080, -4.7690, 280),
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
                            SurfSpotItem("Hondarribia", 43.385, -1.792, 330),
                            SurfSpotItem("San Sebastián", 43.324, -1.975, 350),
                            SurfSpotItem("Zarautz", 43.287, -2.164, 350),
                            SurfSpotItem("Mundaka", 43.407, -2.698, 350),
                            SurfSpotItem("Sopelana", 43.385, -2.997, 330)
                        )),
                        SurfSubRegionItem("Cantabrie & Asturies", listOf(
                            SurfSpotItem("Laredo", 43.415, -3.428, 10),
                            SurfSpotItem("Berria", 43.465, -3.464, 350),
                            SurfSpotItem("Somo", 43.456, -3.743, 20),
                            SurfSpotItem("Santander", 43.473, -3.785, 30),
                            SurfSpotItem("Oyambre", 43.387, -4.323, 345),
                            SurfSpotItem("Rodiles", 43.5365, -5.367, 350),
                            SurfSpotItem("Gijón", 43.541, -5.656, 0),
                            SurfSpotItem("Xagó", 43.589, -5.835, 350),
                            SurfSpotItem("Salinas", 43.58, -5.956, 350),
                            SurfSpotItem("Navia", 43.547, -6.727, 350)
                        )),
                        SurfSubRegionItem("Galice", listOf(
                            SurfSpotItem("Ribadeo", 43.551, -7.039, 350),
                            SurfSpotItem("Pantín", 43.639, -8.113, 330),
                            SurfSpotItem("Doniños", 43.487, -8.328, 350),
                            SurfSpotItem("A Coruña", 43.371, -8.411, 320),
                            SurfSpotItem("Razo", 43.292, -8.706, 320),
                            SurfSpotItem("Malpica", 43.324, -8.813, 330),
                            SurfSpotItem("Laxe", 43.222, -9.012, 320)
                        ))
                    )
                ),
                SurfRegionItem(
                    name = "Côte Ouest (Galice)",
                    subRegions = listOf(
                        SurfSubRegionItem("Costa da Morte", listOf(
                            SurfSpotItem("Fisterra", 42.906, -9.262, 280),
                            SurfSpotItem("Carnota", 42.832, -9.1, 270)
                        )),
                        SurfSubRegionItem("Rías Baixas", listOf(
                            SurfSpotItem("A Lanzada", 42.443, -8.877, 280),
                            SurfSpotItem("Montalvo", 42.379, -8.871, 250),
                            SurfSpotItem("Patos", 42.138, -8.823, 260),
                            SurfSpotItem("Baiona", 42.122, -8.845, 250)
                        ))
                    )
                ),
                SurfRegionItem(
                    name = "Sud-Ouest (Andalousie atlantique)",
                    subRegions = listOf(
                        SurfSubRegionItem("Huelva", listOf(
                            SurfSpotItem("Isla Canela", 37.185, -7.334, 190),
                            SurfSpotItem("Mazagón", 37.135, -6.825, 200),
                            SurfSpotItem("Matalascañas", 37.0, -6.55, 215)
                        )),
                        SurfSubRegionItem("Cadix", listOf(
                            SurfSpotItem("Cádiz La Victoria", 36.512, -6.287, 260),
                            SurfSpotItem("Sancti Petri", 36.379, -6.206, 260),
                            SurfSpotItem("Roche", 36.332, -6.156, 240),
                            SurfSpotItem("Conil", 36.279, -6.092, 220),
                            SurfSpotItem("El Palmar", 36.241, -6.071, 230),
                            SurfSpotItem("Zahara de los Atunes", 36.138, -5.847, 230)
                        )),
                        SurfSubRegionItem("Détroit de Gibraltar", listOf(
                            SurfSpotItem("Bolonia", 36.089, -5.774, 200),
                            SurfSpotItem("Valdevaqueros", 36.067, -5.685, 190),
                            SurfSpotItem("Los Lances", 36.064, -5.635, 190)
                        ))
                    )
                ),
                SurfRegionItem(
                    name = "Sud-Est & Méditerranée",
                    subRegions = listOf(
                        SurfSubRegionItem("Andalousie méditerranéenne", listOf(
                            SurfSpotItem("Málaga", 36.7, -4.43, 170),
                            SurfSpotItem("Cabo de Gata", 36.738, -2.11, 190)
                        )),
                        SurfSubRegionItem("Murcie & Alicante", listOf(
                            SurfSpotItem("Calblanque", 37.599, -0.748, 140),
                            SurfSpotItem("Alicante San Juan", 38.386, -0.413, 130)
                        )),
                        SurfSubRegionItem("Valence", listOf(
                            SurfSpotItem("Cullera", 39.16, -0.242, 90),
                            SurfSpotItem("Valence Malvarrosa", 39.48, -0.322, 90),
                            SurfSpotItem("Sagunto", 39.67, -0.206, 90)
                        )),
                        SurfSubRegionItem("Catalogne", listOf(
                            SurfSpotItem("Tarragone", 41.113, 1.258, 170),
                            SurfSpotItem("Sitges", 41.23, 1.81, 150),
                            SurfSpotItem("Barcelone", 41.378, 2.196, 150),
                            SurfSpotItem("Palamós", 41.85, 3.128, 110)
                        ))
                    )
                ),
                SurfRegionItem(
                    name = "Îles (Baléares & Canaries)",
                    subRegions = listOf(
                        SurfSubRegionItem("Baléares", listOf(
                            SurfSpotItem("Ibiza Cala Comte", 38.96, 1.22, 270),
                            SurfSpotItem("Playa de Palma", 39.52, 2.76, 200),
                            SurfSpotItem("Cala Mesquida", 39.712, 3.43, 60),
                            SurfSpotItem("Pollença", 39.9, 3.08, 45),
                            SurfSpotItem("Menorca Cavalleria", 40.083, 4.083, 350)
                        )),
                        SurfSubRegionItem("Canaries", listOf(
                            SurfSpotItem("Famara", 29.1, -13.56, 320),
                            SurfSpotItem("El Quemao", 29.097, -13.67, 330),
                            SurfSpotItem("Corralejo", 28.739, -13.865, 350),
                            SurfSpotItem("El Cotillo", 28.685, -14.017, 320),
                            SurfSpotItem("Las Canteras", 28.138, -15.44, 320),
                            SurfSpotItem("El Médano", 28.048, -16.538, 170),
                            SurfSpotItem("Las Américas", 28.058, -16.727, 200),
                            SurfSpotItem("Puerto de la Cruz", 28.417, -16.546, 350)
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
                            SurfSpotItem("Viana do Castelo", 41.688, -8.841, 270),
                            SurfSpotItem("Esposende", 41.534, -8.786, 270),
                            SurfSpotItem("Póvoa de Varzim", 41.373, -8.769, 270),
                            SurfSpotItem("Vila do Conde", 41.354, -8.743, 270),
                            SurfSpotItem("Matosinhos", 41.183, -8.7, 270),
                            SurfSpotItem("Porto", 41.161, -8.687, 270),
                            SurfSpotItem("Espinho", 41.008, -8.646, 270)
                        )),
                        SurfSubRegionItem("Aveiro & Figueira", listOf(
                            SurfSpotItem("Furadouro", 40.867, -8.657, 270),
                            SurfSpotItem("Costa Nova", 40.643, -8.75, 270),
                            SurfSpotItem("Vagueira", 40.552, -8.771, 270),
                            SurfSpotItem("Figueira da Foz", 40.155, -8.872, 270)
                        )),
                        SurfSubRegionItem("Portugal Centre", listOf(
                            SurfSpotItem("São Pedro de Moel", 39.757, -9.03, 270),
                            SurfSpotItem("Nazaré", 39.605, -9.077, 300),
                            SurfSpotItem("Foz do Arelho", 39.435, -9.227, 270),
                            SurfSpotItem("Baleal", 39.373, -9.34, 350),
                            SurfSpotItem("Peniche", 39.355, -9.378, 270),
                            SurfSpotItem("Consolação", 39.331, -9.333, 270),
                            SurfSpotItem("Santa Cruz", 39.133, -9.383, 270),
                            SurfSpotItem("Ribeira d'Ilhas", 38.998, -9.421, 290),
                            SurfSpotItem("Ericeira", 38.963, -9.418, 290)
                        )),
                        SurfSubRegionItem("Lisbonne & Alentejo", listOf(
                            SurfSpotItem("Praia Grande", 38.823, -9.475, 270),
                            SurfSpotItem("Guincho", 38.732, -9.473, 280),
                            SurfSpotItem("Cascais", 38.694, -9.422, 190),
                            SurfSpotItem("Carcavelos", 38.678, -9.334, 190),
                            SurfSpotItem("Costa da Caparica", 38.643, -9.245, 260),
                            SurfSpotItem("Comporta", 38.38, -8.79, 270),
                            SurfSpotItem("Sines", 37.956, -8.87, 270),
                            SurfSpotItem("Porto Covo", 37.856, -8.792, 270),
                            SurfSpotItem("Vila Nova de Milfontes", 37.724, -8.783, 270),
                            SurfSpotItem("Zambujeira do Mar", 37.523, -8.787, 270),
                            SurfSpotItem("Odeceixe", 37.437, -8.796, 270)
                        )),
                        SurfSubRegionItem("Algarve", listOf(
                            SurfSpotItem("Arrifana", 37.299, -8.871, 260),
                            SurfSpotItem("Amado", 37.166, -8.905, 260),
                            SurfSpotItem("Carrapateira", 37.192, -8.903, 270),
                            SurfSpotItem("Sagres", 37.009, -8.94, 230),
                            SurfSpotItem("Zavial", 37.046, -8.887, 190),
                            SurfSpotItem("Lagos", 37.113, -8.671, 190),
                            SurfSpotItem("Albufeira", 37.078, -8.158, 180),
                            SurfSpotItem("Faro", 37.0, -7.998, 180),
                            SurfSpotItem("Tavira", 37.117, -7.633, 180)
                        ))
                    )
                ),
                SurfRegionItem(
                    name = "Îles (Madère & Açores)",
                    subRegions = listOf(
                        SurfSubRegionItem("Madère", listOf(
                            SurfSpotItem("Jardim do Mar", 32.748, -17.206, 230),
                            SurfSpotItem("Paul do Mar", 32.755, -17.234, 250),
                            SurfSpotItem("Porto Moniz", 32.868, -17.17, 340),
                            SurfSpotItem("Funchal Formosa", 32.644, -16.94, 180)
                        )),
                        SurfSubRegionItem("Açores", listOf(
                            SurfSpotItem("Santa Bárbara", 37.825, -25.532, 350),
                            SurfSpotItem("Mosteiros", 37.887, -25.82, 280),
                            SurfSpotItem("Quatro Ribeiras", 38.783, -27.219, 350)
                        ))
                    )
                )
            )
        ),
        SurfCountryItem(
            name = "Maroc",
            regions = listOf(
                SurfRegionItem(
                    name = "Côte Nord & Rabat",
                    subRegions = listOf(
                        SurfSubRegionItem("Tanger & Larache", listOf(
                            SurfSpotItem("Cap Spartel", 35.79, -5.919, 270),
                            SurfSpotItem("Asilah", 35.468, -6.035, 270),
                            SurfSpotItem("Larache", 35.199, -6.166, 270)
                        )),
                        SurfSubRegionItem("Rabat & Kénitra", listOf(
                            SurfSpotItem("Mehdia", 34.26, -6.67, 270),
                            SurfSpotItem("Rabat", 34.033, -6.834, 300),
                            SurfSpotItem("Bouznika", 33.786, -7.162, 320),
                            SurfSpotItem("Mohammedia", 33.7, -7.37, 330)
                        ))
                    )
                ),
                SurfRegionItem(
                    name = "Casablanca & Doukkala",
                    subRegions = listOf(
                        SurfSubRegionItem("Casablanca", listOf(
                            SurfSpotItem("Ain Diab", 33.592, -7.674, 320),
                            SurfSpotItem("Dar Bouazza", 33.537, -7.8, 330)
                        )),
                        SurfSubRegionItem("El Jadida & Safi", listOf(
                            SurfSpotItem("Sidi Bouzid", 33.231, -8.547, 300),
                            SurfSpotItem("Oualidia", 32.736, -9.037, 280),
                            SurfSpotItem("Safi", 32.299, -9.243, 300)
                        ))
                    )
                ),
                SurfRegionItem(
                    name = "Essaouira",
                    subRegions = listOf(
                        SurfSubRegionItem("Essaouira", listOf(
                            SurfSpotItem("Essaouira", 31.509, -9.77, 280),
                            SurfSpotItem("Sidi Kaouki", 31.358, -9.801, 300)
                        ))
                    )
                ),
                SurfRegionItem(
                    name = "Agadir & Taghazout",
                    subRegions = listOf(
                        SurfSubRegionItem("Imsouane", listOf(
                            SurfSpotItem("Imsouane", 30.843, -9.823, 290),
                            SurfSpotItem("Imi Ouaddar", 30.659, -9.808, 300)
                        )),
                        SurfSubRegionItem("Taghazout & Agadir", listOf(
                            SurfSpotItem("Killer Point", 30.551, -9.715, 300),
                            SurfSpotItem("Taghazout", 30.544, -9.714, 300),
                            SurfSpotItem("Banana Beach", 30.49, -9.66, 300),
                            SurfSpotItem("Tamraght", 30.48, -9.685, 300),
                            SurfSpotItem("Agadir", 30.412, -9.608, 270)
                        ))
                    )
                ),
                SurfRegionItem(
                    name = "Sud & Sahara",
                    subRegions = listOf(
                        SurfSubRegionItem("Anti-Atlas", listOf(
                            SurfSpotItem("Mirleft", 29.587, -9.981, 270),
                            SurfSpotItem("Legzira", 29.425, -10.05, 270),
                            SurfSpotItem("Sidi Ifni", 29.367, -10.177, 270)
                        )),
                        SurfSubRegionItem("Sahara", listOf(
                            SurfSpotItem("Dakhla", 23.714, -15.937, 300)
                        ))
                    )
                ),
                SurfRegionItem(
                    name = "Méditerranée",
                    subRegions = listOf(
                        SurfSubRegionItem("Rif & Oriental", listOf(
                            SurfSpotItem("Martil", 35.62, -5.276, 80),
                            SurfSpotItem("Al Hoceima", 35.244, -3.932, 10),
                            SurfSpotItem("Saïdia", 35.09, -2.23, 350)
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
