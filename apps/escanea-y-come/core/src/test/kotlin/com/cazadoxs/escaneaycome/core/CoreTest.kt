package com.cazadoxs.escaneaycome.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeTest {
    @Test fun validEan13() = assertEquals("8480000610065", Barcode.normalize(" 8480000610065 "))
    @Test fun badCheckDigit() = assertNull(Barcode.normalize("8480000610066"))
    @Test fun upcAToEan13() = assertEquals("0036000291452", Barcode.normalize("036000291452"))
    @Test fun ean8() = assertEquals("96385074", Barcode.normalize("96385074"))
    @Test fun rejectsInjection() {
        assertNull(Barcode.normalize("8480000610065/../../x"))
        assertNull(Barcode.normalize("84800006100?a=1"))
        assertNull(Barcode.normalize("123"))
    }
    @Test fun upcE() = assertEquals("0042100005264", Barcode.normalize("04252614"))
    @Test fun inStore() = assertTrue(Barcode.isInStoreCode("2000000000008"))
}

class NutritionTest {
    @Test fun scales() {
        val t = Per100(250.0, 10.0, 30.0, 8.0).forGrams(40.0)
        assertEquals(100.0, t.kcal, 1e-9)
        assertEquals(4.0, t.protein, 1e-9)
    }
    @Test fun kjFallback() {
        val p = Per100.sanitized(null, 418.4, 1.0, 2.0, 3.0)!!
        assertEquals(100.0, p.kcal, 1e-6)
    }
    @Test fun atwaterFallbackAndClamp() {
        val p = Per100.sanitized(null, null, 10.0, 10.0, 500.0)!!
        assertEquals(100.0, p.fat, 0.0)
        assertEquals(950.0, p.kcal, 0.0)
        assertNull(Per100.sanitized(null, null, null, null, null))
    }
    @Test fun formats() {
        assertEquals("12,5", 12.46.gramsText())
        assertEquals("3", 3.0.gramsText())
        assertEquals(12.5, parseAmount("12,5")!!, 0.0)
        assertNull(parseAmount("-1"))
        assertNull(parseAmount("NaN"))
    }
    @Test fun split() {
        val s = Totals(0.0, 25.0, 50.0, 0.0).energySplit()
        assertEquals(1.0 / 3, s.protein, 1e-9)
    }
}

class MealTest {
    @Test fun spanishTimetable() {
        assertEquals(Meal.DESAYUNO, Meal.forTime(8))
        assertEquals(Meal.COMIDA, Meal.forTime(14, 30))
        assertEquals(Meal.MERIENDA, Meal.forTime(18))
        assertEquals(Meal.CENA, Meal.forTime(21, 30))
        assertEquals(Meal.PICOTEO, Meal.forTime(1))
        assertEquals(Meal.PICOTEO, Meal.fromName("nope"))
    }
}

class GoalsTest {
    private val adult = Profile(Sex.MUJER, 30, 165.0, 60.0, Activity.SEDENTARIA, GoalType.PERDER)

    @Test fun deficitNeverBelowFloor() {
        val kcal = Goals.dailyKcal(adult)
        assertTrue(kcal >= 1200)
        assertTrue(kcal >= Goals.bmr(adult))
    }
    @Test fun minorsGetMaintenance() {
        val teen = adult.copy(age = 16)
        assertEquals(Goals.dailyKcal(teen.copy(goal = GoalType.MANTENER)), Goals.dailyKcal(teen))
    }
    @Test fun macrosNormalize() {
        val m = Goals.macros(2000, 50, 90, 60) // adds to 200, treated as 25/45/30
        assertEquals(125, m.proteinG)
        assertEquals(225, m.carbsG)
        assertEquals(67, m.fatG)
    }
    @Test fun plausibility() = assertFalse(Goals.isPlausible(adult.copy(heightCm = 20.0)))
}

class StatsTest {
    @Test fun streakAndAverages() {
        assertEquals(3, Stats.streak(setOf(8L, 9L, 10L), 11L))
        assertEquals(2, Stats.streak(setOf(9L, 10L), 10L))
        val items = listOf(
            StatItem(1, "Pan", Totals(200.0, 6.0, 40.0, 1.0)),
            StatItem(3, "Pan", Totals(100.0, 3.0, 20.0, 0.5)),
            StatItem(3, "Huevo", Totals(150.0, 12.0, 1.0, 10.0)),
        )
        val days = Stats.byDay(items, 1, 4)
        assertEquals(4, days.size)
        val avg = Stats.averages(days)
        assertEquals(2, avg.loggedDays)
        assertEquals(225.0, avg.average.kcal, 1e-9)
        assertEquals("Huevo", Stats.topFoods(items, 1) { it.protein }.first().name)
        assertEquals(listOf(1.0, 1.5, 2.5), Stats.movingAverage(listOf(1.0, 2.0, 3.0), 2))
    }
}

class CsvTest {
    @Test fun escapes() {
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
        assertEquals("'=HYPERLINK(1)", Csv.escape("=HYPERLINK(1)"))
        assertEquals("1.5", Csv.number(1.5))
    }
}

class OffParserTest {
    private val sample = """
        {"code":"8480000610065","status":1,"product":{
          "code":"8480000610065","product_name":"Hummus","product_name_es":"Hummus clásico",
          "brands":"Hacendado, Mercadona","quantity":"240 g","serving_size":"30 g","serving_quantity":"30",
          "nutriscore_grade":"C","nova_group":3,
          "image_front_small_url":"https://images.openfoodfacts.org/images/products/848/000/061/0065/front_es.3.200.jpg",
          "allergens_tags":["en:sesame-seeds"],"labels_tags":["en:vegan","en:no-palm-oil"],
          "nutriments":{"energy-kcal_100g":305,"proteins_100g":"7,2","carbohydrates_100g":9.1,"fat_100g":26,
            "sugars_100g":0.5,"salt_100g":1.1,"fiber_100g":null}}}
    """.trimIndent()

    @Test fun parsesProduct() {
        val r = OffParser.parseProductResponse(sample, "8480000610065") as OffLookup.Found
        val p = r.product
        assertEquals("Hummus clásico", p.name)
        assertEquals("Hacendado", p.brand)
        assertEquals(305.0, p.per100.kcal, 0.0)
        assertEquals(7.2, p.per100.protein, 1e-9)
        assertNull(p.per100.fiber)
        assertEquals(30.0, p.servingGrams!!, 0.0)
        assertEquals("c", p.nutriScore)
        assertEquals(3, p.nova)
        assertEquals(listOf("sesame seeds"), p.allergens)
        assertEquals("Sésamo", Labels.allergen(p.allergens.first()))
        assertEquals(listOf("Vegano", "Sin aceite de palma"), Labels.claims(p.labels))
    }

    @Test fun notFoundAndGarbage() {
        assertEquals(OffLookup.NotFound, OffParser.parseProductResponse("""{"status":0}""", "1"))
        assertEquals(OffLookup.NotFound, OffParser.parseProductResponse("<html>", "1"))
        val incomplete = OffParser.parseProductResponse("""{"status":1,"product":{"product_name":"X"}}""", "96385074")
        assertTrue(incomplete is OffLookup.Incomplete)
    }

    @Test fun rejectsForeignImages() {
        assertNull(OffParser.safeImageUrl("http://images.openfoodfacts.org/a.jpg"))
        assertNull(OffParser.safeImageUrl("https://evil.example/a.jpg"))
        assertNull(OffParser.safeImageUrl("https://user@images.openfoodfacts.org/a.jpg"))
    }

    @Test fun search() {
        val json = """{"products":[{"code":"8480000610065","product_name":"A","nutriments":{"energy-kcal_100g":10}},
            {"code":"bad","product_name":"B","nutriments":{"energy-kcal_100g":10}},
            {"code":"96385074","product_name":"C"}]}"""
        val list = OffParser.parseSearchResponse(json)
        assertEquals(listOf("A"), list.map { it.name })
    }
}
