package com.tumuyan.ncnn.realsr.ui

import android.content.Context
import com.tumuyan.ncnn.realsr.core.database.RealScaleDatabase
import com.tumuyan.ncnn.realsr.core.engine.ProcessingServiceEngine
import com.tumuyan.ncnn.realsr.core.engine.UpscaleEngine
import com.tumuyan.ncnn.realsr.core.model.ModelRepository

class RealScaleAppContainer(
    context: Context,
    val database: RealScaleDatabase = RealScaleDatabase.getInstance(context.applicationContext),
    val engine: UpscaleEngine = ProcessingServiceEngine(
        context = context.applicationContext,
        recentCreationDao = database.recentCreationDao()
    ),
    val modelRepository: ModelRepository = ModelRepository(
        context = context.applicationContext,
        downloadedModelDao = database.downloadedModelDao()
    )
) {
    companion object {
        @Volatile
        private var INSTANCE: RealScaleAppContainer? = null

        fun getInstance(context: Context): RealScaleAppContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RealScaleAppContainer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
