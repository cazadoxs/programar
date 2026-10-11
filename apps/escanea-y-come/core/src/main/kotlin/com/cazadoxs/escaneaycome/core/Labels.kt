package com.cazadoxs.escaneaycome.core

/** Spanish names for the EU's 14 declarable allergens and common label claims. */
object Labels {
    private val ALLERGENS = mapOf(
        "gluten" to "Gluten",
        "crustaceans" to "Crustáceos",
        "eggs" to "Huevo",
        "fish" to "Pescado",
        "peanuts" to "Cacahuetes",
        "soybeans" to "Soja",
        "milk" to "Leche",
        "nuts" to "Frutos de cáscara",
        "celery" to "Apio",
        "mustard" to "Mostaza",
        "sesame seeds" to "Sésamo",
        "sulphur dioxide and sulphites" to "Sulfitos",
        "lupin" to "Altramuces",
        "molluscs" to "Moluscos",
    )

    private val CLAIMS = mapOf(
        "vegan" to "Vegano",
        "vegetarian" to "Vegetariano",
        "organic" to "Ecológico",
        "eu organic" to "Ecológico UE",
        "no gluten" to "Sin gluten",
        "gluten free" to "Sin gluten",
        "no lactose" to "Sin lactosa",
        "lactose free" to "Sin lactosa",
        "palm oil free" to "Sin aceite de palma",
        "no palm oil" to "Sin aceite de palma",
        "no added sugar" to "Sin azúcar añadido",
        "high protein" to "Alto en proteínas",
        "high fibres" to "Alto en fibra",
        "source of fibre" to "Fuente de fibra",
    )

    fun allergen(tag: String): String = ALLERGENS[tag.lowercase()] ?: tag.replaceFirstChar { it.uppercase() }

    /** Only claims we can translate are shown; the raw tag list is long and noisy. */
    fun claims(tags: List<String>): List<String> = tags.mapNotNull { CLAIMS[it.lowercase()] }.distinct()

    fun novaText(nova: Int): String = when (nova) {
        1 -> "Sin procesar o mínimamente procesado"
        2 -> "Ingrediente culinario procesado"
        3 -> "Alimento procesado"
        4 -> "Ultraprocesado"
        else -> ""
    }
}
