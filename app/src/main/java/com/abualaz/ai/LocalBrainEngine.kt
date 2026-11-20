package com.abualaz.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig

/**
 * Single-owner wrapper for LiteRT-LM. Call load/ask/close from one background executor.
 * The model file stays outside the APK in app-private storage.
 */
class LocalBrainEngine(
    private val modelPath: String,
    private val cachePath: String
) : AutoCloseable {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    fun load() {
        if (engine != null && conversation != null) return
        val candidate = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = cachePath
            )
        )
        try {
            candidate.initialize()
            val chat = candidate.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "أنت عبود، مساعد شخصي عربي. أجب بوضوح وبلغة المستخدم، " +
                        "واختصر عند الأسئلة البسيطة. لا تدّع تنفيذ إجراءات على الهاتف."
                    )
                )
            )
            engine = candidate
            conversation = chat
        } catch (error: Exception) {
            // Engine must not be closed if initialize did not complete successfully.
            throw error
        }
    }

    fun ask(prompt: String): String {
        val active = conversation ?: throw IllegalStateException("العقل المحلي غير محمّل.")
        return active.sendMessage(prompt).toString().trim()
    }

    override fun close() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }
}
