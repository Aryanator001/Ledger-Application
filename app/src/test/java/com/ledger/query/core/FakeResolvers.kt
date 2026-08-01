package com.ledger.query.core

/** Fixed dataset — no mocking framework, no DB, no network. */
object FakeEntityResolver : QueryRouter.EntityResolver {

    private data class Entity(val id: Long, val name: String)

    private val entities = listOf(
        Entity(1, "Vijay Kumar"),
        Entity(2, "Vijaykumar Shah"),
        Entity(3, "Ramesh"),
        Entity(4, "Suresh Patel"),
        Entity(5, "X")
    )

    // All single tokens from every entity name, lowercased.
    // O(1) membership check for couldBeEntity().
    private val entityTokens: Set<String> = entities
        .flatMap { it.name.lowercase().split(" ") }
        .toSet()

    // Full lowercased names and common joined forms, for O(1) full-name lookup.
    private val entityStrings: Set<String> = entities
        .flatMap { e ->
            listOf(e.name.lowercase()) + e.name.lowercase().split(" ")
        }.toSet()

    override fun couldBeEntity(token: String): Boolean =
        token.lowercase() in entityStrings

    override fun resolve(surfaceForm: String): EntityRef {
        val lower = surfaceForm.lowercase().trim()

        // Find all entities whose name contains surfaceForm as an exact token
        // or as a prefix of any token (handles "vijay" → "Vijaykumar Shah").
        val matches = entities.filter { entity ->
            val nameTokens = entity.name.lowercase().split(" ")
            nameTokens.any { nt -> nt == lower || nt.startsWith(lower) } ||
                entity.name.lowercase() == lower
        }

        return when (matches.size) {
            0 -> EntityRef(surfaceForm, null, null, 0.0f)
            1 -> {
                val e = matches[0]
                EntityRef(surfaceForm, e.id, e.name, 1.0f)
            }
            else -> EntityRef(
                surfaceForm,
                id = null,
                canonicalName = null,
                confidence = 0.5f,
                ambiguousWith = matches.map { it.name }
            )
        }
    }
}

object FakeCategoryResolver : QueryRouter.CategoryResolver {

    private val categories: Map<String, Pair<Long, String>> = mapOf(
        "food"      to (10L to "Food"),
        "khana"     to (10L to "Food"),
        "khaana"    to (10L to "Food"),
        "rent"      to (11L to "Rent"),
        "kiraya"    to (11L to "Rent"),
        "transport" to (12L to "Transport"),
        "travel"    to (12L to "Transport"),
        "salary"    to (13L to "Salary"),
        "salary"    to (13L to "Salary"),
        "groceries" to (10L to "Food"),
        "grocery"   to (10L to "Food")
    )

    override fun couldBeCategory(token: String): Boolean =
        token.lowercase() in categories

    override fun resolve(surfaceForm: String): CategoryRef {
        val entry = categories[surfaceForm.lowercase()]
        return if (entry != null) {
            CategoryRef(surfaceForm, entry.first, entry.second, 1.0f)
        } else {
            CategoryRef(surfaceForm, null, null, 0.0f)
        }
    }
}
