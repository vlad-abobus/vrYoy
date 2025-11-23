package com.vryo.app.utils

import com.vryo.app.model.Quaternion
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit тести для SensorHelper та допоміжних функцій
 */
class SensorHelperTest {
    
    @Test
    fun testQuaternionIdentity() {
        val identity = Quaternion.identity()
        assertEquals(1f, identity.w, 0.001f)
        assertEquals(0f, identity.x, 0.001f)
        assertEquals(0f, identity.y, 0.001f)
        assertEquals(0f, identity.z, 0.001f)
    }
    
    @Test
    fun testVector3Normalize() {
        val vector = com.vryo.app.model.Vector3(3f, 4f, 0f)
        val normalized = vector.normalize()
        val length = normalized.length()
        assertEquals(1f, length, 0.001f)
    }
    
    @Test
    fun testVector3Length() {
        val vector = com.vryo.app.model.Vector3(3f, 4f, 0f)
        val length = vector.length()
        assertEquals(5f, length, 0.001f)
    }
}

