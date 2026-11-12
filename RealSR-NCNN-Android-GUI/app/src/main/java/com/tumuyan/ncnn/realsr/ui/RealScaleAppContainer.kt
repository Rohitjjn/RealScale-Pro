package com.tumuyan.ncnn.realsr.ui

import android.content.Context
import com.tumuyan.ncnn.realsr.core.database.RealScaleDatabase
import com.tumuyan.ncnn.realsr.core.engine.ProcessingServiceEngine
import com.tumuyan.ncnn.realsr.core.engine.UpscaleEngine
import com.tumuyan.ncnn.realsr.core.model.ModelRepository

class RealScaleAppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: RealScaleDatabase = RealScaleDatabase.getInstance(appContext)
    val engine: UpscaleEngine = ProcessingServiceEngine(
        context = appContext,
        recentCreationDao = database.recentCreationDao()
    )
    val modelRepository: ModelRepository = ModelRepository(
        context = appContext,
        downloadedModelDao = database.downloadedModelDao()
    )

    companion object {
        @Volatile
        private var INSTANCE: RealScaleAppContainer? = null

        fun getInstance(context: Context): RealScaleAppContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RealScaleAppContainer(context).also { INSTANCE = it }
            }
        }
    }
}
