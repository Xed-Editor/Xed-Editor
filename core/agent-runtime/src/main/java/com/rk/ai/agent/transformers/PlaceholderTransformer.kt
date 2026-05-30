package com.rk.ai.agent.transformers

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.rk.ai.providers.Model
import com.rk.ai.models.UIMessage
import com.rk.ai.models.UIMessagePart
import com.rk.ai.persistence.settings.Settings
import com.rk.ai.models.Assistant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.Temporal
import java.util.Locale
import java.util.TimeZone

data class PlaceholderCtx(
    val context: Context,
    val settings: Settings,
    val model: Model,
    val assistant: Assistant,
)

interface PlaceholderProvider {
    val placeholders: Map<String, PlaceholderInfo>
}

data class PlaceholderInfo(
    val displayName: String,
    val resolver: (PlaceholderCtx) -> String
)

class PlaceholderBuilder {
    private val placeholders = mutableMapOf<String, PlaceholderInfo>()

    fun placeholder(
        key: String,
        displayName: String,
        resolver: (PlaceholderCtx) -> String
    ) {
        placeholders[key] = PlaceholderInfo(displayName, resolver)
    }

    fun build(): Map<String, PlaceholderInfo> = placeholders.toMap()
}

fun buildPlaceholders(block: PlaceholderBuilder.() -> Unit): Map<String, PlaceholderInfo> {
    return PlaceholderBuilder().apply(block).build()
}

object DefaultPlaceholderProvider : PlaceholderProvider {
    override val placeholders: Map<String, PlaceholderInfo> = buildPlaceholders {
        placeholder("cur_date", "Current Date") {
            LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
        }

        placeholder("cur_time", "Current Time") {
            LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
        }

        placeholder("cur_datetime", "Current DateTime") {
            LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
        }

        placeholder("model_id", "Model ID") {
            it.model.modelId
        }

        placeholder("model_name", "Model Name") {
            it.model.displayName
        }

        placeholder("locale", "Locale") {
            Locale.getDefault().displayName
        }

        placeholder("timezone", "Timezone") {
            TimeZone.getDefault().displayName
        }

        placeholder("system_version", "System Version") {
            "Android SDK v${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})"
        }

        placeholder("device_info", "Device Info") {
            "${Build.BRAND} ${Build.MODEL}"
        }

        placeholder("battery_level", "Battery Level") {
            it.context.batteryLevel().toString()
        }

        placeholder("nickname", "Nickname") {
            "user"
        }

        placeholder("char", "Character") {
            it.assistant.name.ifBlank { "assistant" }
        }

        placeholder("user", "User") {
            "user"
        }
    }

    private fun Context.batteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}

object PlaceholderTransformer : InputMessageTransformer {
    private val defaultProvider = DefaultPlaceholderProvider
    private var customProviders: List<PlaceholderProvider> = emptyList()

    fun setCustomProviders(providers: List<PlaceholderProvider>) {
        customProviders = providers
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map {
            it.copy(
                parts = it.parts.map { part ->
                    if (part is UIMessagePart.Text) {
                        part.copy(
                            text = replacePlaceholders(text = part.text, ctx = ctx)
                        )
                    } else {
                        part
                    }
                }
            )
        }
    }

    private fun replacePlaceholders(
        text: String,
        ctx: TransformerContext
    ): String {
        var result = text
        val placeholderCtx = PlaceholderCtx(
            context = ctx.context,
            settings = ctx.settings,
            model = ctx.model,
            assistant = ctx.assistant
        )

        val allProviders = listOf(defaultProvider) + customProviders
        allProviders.forEach { provider ->
            provider.placeholders.forEach { (key, placeholderInfo) ->
                val value = placeholderInfo.resolver(placeholderCtx)
                result = result
                    .replace(oldValue = "{{$key}}", newValue = value, ignoreCase = true)
                    .replace(oldValue = "{$key}", newValue = value, ignoreCase = true)
            }
        }

        return result
    }
}