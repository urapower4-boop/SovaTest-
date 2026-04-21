package ru.sova.testsolver

import android.content.Context
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream

class TestSolver(private val context: Context) {

    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash-vision",
        apiKey = apiKey
    )
    private val httpClient = OkHttpClient()

    suspend fun solveTest(): String {
        Log.d("TestSolver", "Начинаю решать тест...")

        repeat(10) { questionNum ->
            Log.d("TestSolver", "Вопрос ${questionNum + 1} из 10")

            // Захватываем скриншот
            val screenshot = captureScreenshot()
            if (screenshot == null) {
                Log.w("TestSolver", "Не удалось захватить скриншот")
                delay(5000)
                return@repeat
            }

            // Отправляем на Gemini
            val result = analyzeWithGemini(screenshot)
            Log.d("TestSolver", "Результат: $result")

            // Ждём 45 сек перед следующим вопросом
            delay(45000)
        }

        return "✅ Тест завершён"
    }

    private suspend fun captureScreenshot(): Bitmap? {
        return try {
            // Используем MediaProjection для снимка без записи в галерею
            // Это требует запроса разрешения у пользователя
            // Для учебной версии — захватываем View, если это возможно
            
            Log.d("TestSolver", "Скриншот подготовлен")
            // Placeholder: в production здесь будет MediaProjection
            null
        } catch (e: Exception) {
            Log.e("TestSolver", "Ошибка скриншота", e)
            null
        }
    }

    private suspend fun analyzeWithGemini(screenshot: Bitmap): String {
        return try {
            val base64Image = bitmapToBase64(screenshot)

            val response = generativeModel.generateContent(
                content {
                    image(android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888))
                    text("""
                    Ты помощник для решения тестов. 
                    Анализируй скриншот и определи:
                    1. Текст вопроса
                    2. Все варианты ответов
                    3. Тип задания (один ответ / несколько / хронология / текст)
                    4. Правильный ответ с кратким объяснением
                    
                    Ответь в формате:
                    ВОПРОС: [текст]
                    ВАРИАНТ А: [текст] 
                    ВАРИАНТ Б: [текст]
                    ПРАВИЛЬНЫЙ: [буква и объяснение]
                    ТИП: [single/multiple/order/text]
                    """.trimIndent())
                }
            )

            response.text ?: "Ошибка анализа"
        } catch (e: Exception) {
            Log.e("TestSolver", "Ошибка Gemini", e)
            "Ошибка API"
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val imageBytes = outputStream.toByteArray()
        return android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
    }
}
