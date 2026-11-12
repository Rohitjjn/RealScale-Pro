package com.tumuyan.ncnn.realsr

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FoundationSmokeTest {
    @Test
    fun kotlinAndCoroutinesInteroperateWithUpstreamJava() = runTest {
        val cmd = CommandBuilder()
            .append("./resize-ncnn")
            .append("-i", "input.png")
            .append("-o", "output.png")
            .append("-m", "avir-lancir")
            .append("-s", 2)
            .build()
        assertEquals("./resize-ncnn -i input.png -o output.png -m avir-lancir -s 2", cmd)
    }
}
