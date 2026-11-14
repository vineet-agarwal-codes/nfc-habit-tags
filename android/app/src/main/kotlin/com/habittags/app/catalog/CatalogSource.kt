package com.habittags.app.catalog

import android.content.Context
import com.habittags.core.recipe.Catalog
import com.habittags.core.recipe.parseCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CatalogSource {
    suspend fun load(): Catalog
}

class BundledCatalogSource(private val context: Context) : CatalogSource {
    override suspend fun load(): Catalog = withContext(Dispatchers.IO) {
        parseCatalog(context.assets.open("catalog.json").bufferedReader().use { it.readText() })
    }
}
