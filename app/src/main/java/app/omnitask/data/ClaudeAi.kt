package app.omnitask.data

import app.omnitask.model.SecretaryAi
import app.omnitask.model.tr
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCountTokensParams
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * The secretary chat's calls to Claude, through Anthropic's SDK with the owner's own key. Two calls: counting what a
 * question sends (free, so the confirmation shows the real figure) and asking it. Blocking: call off the main thread.
 * The key never leaves this phone except to Anthropic.
 */
class ClaudeAi(private val key: String) {

    private val client: AnthropicClient by lazy { AnthropicOkHttpClient.builder().apiKey(key).maxRetries(1).build() }

    /** What came back: the model's JSON, and the tokens it really used (thinking is part of [outTokens]). */
    data class Answer(val json: String, val inTokens: Long, val outTokens: Long)

    /** A call that did not give an answer, with words for the owner. */
    class Failure(message: String) : Exception(message)

    private fun outputConfig(effort: SecretaryAi.Effort): OutputConfig {
        @Suppress("UNCHECKED_CAST")
        val schema = ObjectMapper().readValue(SecretaryAi.SCHEMA, Map::class.java) as Map<String, Any?>
        val format = JsonOutputFormat.builder()
            .schema(JsonOutputFormat.Schema.builder().apply { schema.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) } }.build())
            .build()
        val level = when (effort) {
            SecretaryAi.Effort.LOW -> OutputConfig.Effort.LOW
            SecretaryAi.Effort.MEDIUM -> OutputConfig.Effort.MEDIUM
            SecretaryAi.Effort.HIGH -> OutputConfig.Effort.HIGH
        }
        return OutputConfig.builder().effort(level).format(format).build()
    }

    /** The input tokens [question] sends with [system] on [mode], counted by the API (free of charge). */
    fun count(mode: SecretaryAi.Mode, system: String, question: String): Long = guard {
        val model = mode.modelId ?: return@guard 0L
        client.messages().countTokens(
            MessageCountTokensParams.builder().model(model).system(system).addUserMessage(question).build(),
        ).inputTokens()
    }

    /** Asks [question] on [mode] at [effort]; the answer follows [SecretaryAi.SCHEMA]. */
    fun ask(mode: SecretaryAi.Mode, effort: SecretaryAi.Effort, system: String, question: String): Answer = guard {
        val model = mode.modelId ?: throw Failure(tr("ยังไม่ได้เลือกรุ่น AI", "No model picked"))
        val message = client.messages().create(
            MessageCreateParams.builder()
                .model(model)
                .maxTokens(8000L)
                .system(system)
                .outputConfig(outputConfig(effort))
                .addUserMessage(question)
                .build(),
        )
        val usage = message.usage()
        val spentIn = usage.inputTokens() + usage.cacheCreationInputTokens().orElse(0L) + usage.cacheReadInputTokens().orElse(0L)
        val text = message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
        when (message.stopReason().orElse(null)) {
            StopReason.REFUSAL -> throw Spent(Failure(tr("AI ปฏิเสธที่จะตอบเรื่องนี้", "The model declined to answer this")), spentIn, usage.outputTokens())
            StopReason.MAX_TOKENS -> throw Spent(Failure(tr("คำตอบยาวเกินกำหนด ลองถามให้สั้นลง", "The answer ran too long; ask more briefly")), spentIn, usage.outputTokens())
            else -> {}
        }
        Answer(text, spentIn, usage.outputTokens())
    }

    /** A failure after the model already answered (so tokens were spent and must still be counted). */
    class Spent(val failure: Failure, val inTokens: Long, val outTokens: Long) : Exception(failure.message)

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: Spent) {
        throw e
    } catch (e: Failure) {
        throw e
    } catch (e: UnauthorizedException) {
        throw Failure(tr("กุญแจ API ใช้ไม่ได้ ตรวจกุญแจในตั้งค่า AI", "The API key does not work; check it in the AI settings"))
    } catch (e: PermissionDeniedException) {
        throw Failure(tr("กุญแจนี้ไม่มีสิทธิ์ใช้รุ่นนี้", "This key may not use this model"))
    } catch (e: NotFoundException) {
        throw Failure(tr("ไม่พบรุ่น AI นี้ อาจเลิกให้บริการแล้ว", "This model was not found; it may have been retired"))
    } catch (e: RateLimitException) {
        throw Failure(tr("ถามถี่เกินไป หรือเครดิตหมด รอสักครู่แล้วลองใหม่", "Too many questions or no credit left; try again in a moment"))
    } catch (e: AnthropicServiceException) {
        throw Failure(tr("Anthropic ตอบผิดพลาด (${e.statusCode()}) ลองใหม่อีกครั้ง", "Anthropic returned an error (${e.statusCode()}); try again"))
    } catch (e: AnthropicIoException) {
        throw Failure(tr("ต่อเน็ตไม่ได้ ลองใหม่เมื่อมีสัญญาณ", "No connection; try again when online"))
    } catch (e: RuntimeException) {
        throw Failure(tr("เรียก AI ไม่สำเร็จ: ${e.message ?: e.javaClass.simpleName}", "The call failed: ${e.message ?: e.javaClass.simpleName}"))
    }
}
