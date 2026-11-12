package dev.realscale.pro

import android.app.Application
import dev.realscale.pro.core.database.AppDatabase
import dev.realscale.pro.core.engine.NcnnEngine
import dev.realscale.pro.data.models.ModelRepository

class RealScaleApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.create(this) }
    val engine: NcnnEngine by lazy { NcnnEngine(this) }
    val modelRepo: ModelRepository by lazy { ModelRepository(this, db.models()) }
}
