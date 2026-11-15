package com.tumuyan.ncnn.realsr.core.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard for the Model Manager bug where every model row looked like a running download: the store
 * mapped every catalog state into `downloads`, including `NOT_INSTALLED`, so the cards showed a dead
 * 0 % ring with "Tap to cancel" and no download button.
 */
class ModelInstallStatusTest {

    @Test
    fun onlyRealTransfersAreDrawnAsDownloadProgress() {
        assertTrue(ModelInstallStatus.QUEUED.isActiveTransfer)
        assertTrue(ModelInstallStatus.DOWNLOADING.isActiveTransfer)
        assertTrue(ModelInstallStatus.VERIFYING.isActiveTransfer)
        assertTrue(ModelInstallStatus.PAUSED.isActiveTransfer)
        assertTrue(ModelInstallStatus.FAILED.isActiveTransfer)

        assertFalse("A model nobody started is not a download", ModelInstallStatus.NOT_INSTALLED.isActiveTransfer)
        assertFalse("An installed model is not a download", ModelInstallStatus.INSTALLED.isActiveTransfer)
    }
}
