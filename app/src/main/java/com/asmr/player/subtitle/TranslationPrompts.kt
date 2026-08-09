package com.asmr.player.subtitle

/**
 * 运行时解码后的 LLM 翻译提示词。
 *
 * 明文提示词存放在本机 gitignore 的 `prompts/` 目录，通过
 * `tools/encode_prompts.py` 编码为 XOR + Base64 后提交到仓库
 * （见 [TranslationPromptsEncoded]）；运行时在此处解码供调用方使用。
 */
internal object TranslationPrompts {

    private val prompts: Map<String, String> by lazy {
        val result = HashMap<String, String>()
        for (line in TranslationPromptsEncoded.ENCODED_LINES.lines()) {
            if (line.isBlank()) continue
            val separator = line.indexOf('=')
            require(separator > 0) { "非法提示词编码行" }
            val key = line.substring(0, separator)
            require(result.put(key, TranslationPromptsCodec.decode(line.substring(separator + 1)).trim()) == null) {
                "重复的提示词 key: $key"
            }
        }
        result
    }

    private fun raw(key: String): String = requireNotNull(prompts[key]) {
        "缺少提示词资源: $key（请运行 python tools/encode_prompts.py 重新生成）"
    }

    internal fun displayNameSystemPrompt(): String = raw(KEY_DISPLAY_NAME_SYSTEM_PROMPT)

    internal fun subtitleAgentSystemPromptTemplate(): String = raw(KEY_SUBTITLE_AGENT_SYSTEM_PROMPT_TEMPLATE)

    internal fun subtitlePolishSystemPromptTemplate(): String = raw(KEY_SUBTITLE_POLISH_SYSTEM_PROMPT)

    internal fun subtitlePolishToolReadDescription(): String = raw(KEY_SUBTITLE_POLISH_TOOL_READ_DESCRIPTION)

    internal fun subtitlePolishToolWriteDescription(): String = raw(KEY_SUBTITLE_POLISH_TOOL_WRITE_DESCRIPTION)

    internal fun subtitlePolishInitialUserMessage(): String = raw(KEY_SUBTITLE_POLISH_INITIAL_USER_MESSAGE)

    internal fun subtitlePolishProgressInstruction(): String = raw(KEY_SUBTITLE_POLISH_PROGRESS_INSTRUCTION)

    internal fun subtitlePolishContinueMessageGeneric(): String = raw(KEY_SUBTITLE_POLISH_CONTINUE_MESSAGE_GENERIC)

    internal fun subtitleStyleGuide(): String = raw(KEY_SUBTITLE_STYLE_GUIDE)

    internal fun subtitleReferenceTable(): String = raw(KEY_SUBTITLE_REFERENCE_TABLE)

    internal fun subtitleSegmentationRulesMerge(): String = raw(KEY_SUBTITLE_SEGMENTATION_RULES_MERGE)

    internal fun subtitleSegmentationRulesNoMerge(): String = raw(KEY_SUBTITLE_SEGMENTATION_RULES_NO_MERGE)

    internal fun subtitleProgressInstruction(): String = raw(KEY_SUBTITLE_PROGRESS_INSTRUCTION)

    internal fun subtitleContinueMessageLengthLimited(): String = raw(KEY_SUBTITLE_CONTINUE_MESSAGE_LENGTH)

    internal fun subtitleContinueMessageGeneric(): String = raw(KEY_SUBTITLE_CONTINUE_MESSAGE_GENERIC)

    internal fun subtitleInitialUserMessage(): String = raw(KEY_SUBTITLE_INITIAL_USER_MESSAGE)

    internal fun subtitleToolErrorInstruction(): String = raw(KEY_SUBTITLE_TOOL_ERROR_INSTRUCTION)

    internal fun subtitleToolReadDescription(): String = raw(KEY_SUBTITLE_TOOL_READ_DESCRIPTION)

    internal fun subtitleToolWriteDescription(): String = raw(KEY_SUBTITLE_TOOL_WRITE_DESCRIPTION)

    internal fun subtitleToolChineseFieldDescription(): String = raw(KEY_SUBTITLE_TOOL_CHINESE_FIELD_DESCRIPTION)

    internal const val KEY_DISPLAY_NAME_SYSTEM_PROMPT = "display_name_system_prompt"
    internal const val KEY_SUBTITLE_AGENT_SYSTEM_PROMPT_TEMPLATE = "subtitle_agent_system_prompt_template"
    internal const val KEY_SUBTITLE_POLISH_SYSTEM_PROMPT = "subtitle_polish_system_prompt"
    internal const val KEY_SUBTITLE_POLISH_TOOL_READ_DESCRIPTION = "subtitle_polish_tool_read_description"
    internal const val KEY_SUBTITLE_POLISH_TOOL_WRITE_DESCRIPTION = "subtitle_polish_tool_write_description"
    internal const val KEY_SUBTITLE_POLISH_INITIAL_USER_MESSAGE = "subtitle_polish_initial_user_message"
    internal const val KEY_SUBTITLE_POLISH_PROGRESS_INSTRUCTION = "subtitle_polish_progress_instruction"
    internal const val KEY_SUBTITLE_POLISH_CONTINUE_MESSAGE_GENERIC = "subtitle_polish_continue_message_generic"
    internal const val KEY_SUBTITLE_STYLE_GUIDE = "subtitle_style_guide"
    internal const val KEY_SUBTITLE_REFERENCE_TABLE = "subtitle_reference_table"
    internal const val KEY_SUBTITLE_SEGMENTATION_RULES_MERGE = "subtitle_segmentation_rules_merge"
    internal const val KEY_SUBTITLE_SEGMENTATION_RULES_NO_MERGE = "subtitle_segmentation_rules_no_merge"
    internal const val KEY_SUBTITLE_PROGRESS_INSTRUCTION = "subtitle_progress_instruction"
    internal const val KEY_SUBTITLE_CONTINUE_MESSAGE_LENGTH = "subtitle_continue_message_length"
    internal const val KEY_SUBTITLE_CONTINUE_MESSAGE_GENERIC = "subtitle_continue_message_generic"
    internal const val KEY_SUBTITLE_INITIAL_USER_MESSAGE = "subtitle_initial_user_message"
    internal const val KEY_SUBTITLE_TOOL_ERROR_INSTRUCTION = "subtitle_tool_error_instruction"
    internal const val KEY_SUBTITLE_TOOL_READ_DESCRIPTION = "subtitle_tool_read_description"
    internal const val KEY_SUBTITLE_TOOL_WRITE_DESCRIPTION = "subtitle_tool_write_description"
    internal const val KEY_SUBTITLE_TOOL_CHINESE_FIELD_DESCRIPTION = "subtitle_tool_chinese_field_description"
}

/**
 * XOR + Base64 编解码器。密钥必须与 `tools/encode_prompts.py` 中的 `XOR_KEY` 一致。
 * 该方案仅为“仓库不存明文”的混淆，不构成强加密。
 */
internal object TranslationPromptsCodec {

    private const val SUBTITLE_PROMPTS_XOR_KEY = "EaraAsmrPlayer.SubtitlePrompt.V1"

    internal fun decode(encoded: String): String {
        val key = SUBTITLE_PROMPTS_XOR_KEY.toByteArray(Charsets.UTF_8)
        val bytes = Base64Utils.decode(encoded)
        val decoded = ByteArray(bytes.size)
        for (i in bytes.indices) {
            decoded[i] = (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return String(decoded, Charsets.UTF_8)
    }

    internal fun decodeBase64(input: String): ByteArray = Base64Utils.decode(input)
}

/**
 * 标准 Base64 解码（纯 Kotlin，兼容 Android API 24 与 JVM 单元测试）。
 * 只需解码 `tools/encode_prompts.py`（Python 标准库 base64）生成的输入。
 */
internal object Base64Utils {

    private val DECODE_TABLE = IntArray(256) { -1 }.apply {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        for (i in alphabet.indices) set(alphabet[i].code, i)
    }

    internal fun decode(encoded: String): ByteArray {
        var buffer = 0
        var bits = 0
        val output = java.io.ByteArrayOutputStream(encoded.length * 3 / 4 + 3)
        for (char in encoded) {
            if (char == '=') break
            val value = if (char.code < 256) DECODE_TABLE[char.code] else -1
            require(value >= 0) { "非法 Base64 字符: $char" }
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits == 24) {
                output.write((buffer ushr 16) and 0xFF)
                output.write((buffer ushr 8) and 0xFF)
                output.write(buffer and 0xFF)
                buffer = 0
                bits = 0
            }
        }
        when (bits) {
            12 -> output.write((buffer ushr 4) and 0xFF)
            18 -> {
                output.write((buffer ushr 10) and 0xFF)
                output.write((buffer ushr 2) and 0xFF)
            }
        }
        return output.toByteArray()
    }
}
