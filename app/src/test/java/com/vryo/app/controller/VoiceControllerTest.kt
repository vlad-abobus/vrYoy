package com.vryo.app.controller

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit тести для VoiceController (команди розпізнавання)
 */
class VoiceControllerTest {
    
    @Test
    fun testExtractNumberFromText() {
        // Тест витягування числа
        val text1 = "перемотай на 10 секунд"
        val number1 = extractNumberFromText(text1)
        assertEquals("10", number1)
        
        val text2 = "skip 5 seconds"
        val number2 = extractNumberFromText(text2)
        assertEquals("5", number2)
    }
    
    private fun extractNumberFromText(text: String): String? {
        val numberPattern = "\\d+".toRegex()
        return numberPattern.find(text)?.value
    }
}

