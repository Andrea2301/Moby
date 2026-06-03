package com.example.moby.logic.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var currentChunks = emptyList<String>()
    private var currentChunkIndex = 0

    var onChapterFinished: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isPlaying.value = true
                }

                override fun onDone(utteranceId: String?) {
                    if (!_isPaused.value && _isActive.value) {
                        currentChunkIndex++
                        if (currentChunkIndex < currentChunks.size) {
                            playCurrentChunk()
                        } else {
                            // Finished reading chapter
                            _isPlaying.value = false
                            _isActive.value = false
                            onChapterFinished?.invoke()
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    _isPlaying.value = false
                }
            })
        }
    }

    fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed)
        if (_isPlaying.value) {
            // Restart current chunk with new speed
            tts?.stop()
            playCurrentChunk()
        }
    }

    fun startReading(text: String) {
        if (!isInitialized || text.isBlank()) return
        
        currentChunks = chunkText(text)
        currentChunkIndex = 0
        _isActive.value = true
        _isPaused.value = false
        playCurrentChunk()
    }

    private fun playCurrentChunk() {
        if (currentChunkIndex < currentChunks.size) {
            val chunk = currentChunks[currentChunkIndex]
            tts?.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, "chunk_$currentChunkIndex")
        }
    }

    fun pause() {
        _isPaused.value = true
        _isPlaying.value = false
        tts?.stop()
    }

    fun resume() {
        if (_isPaused.value && _isActive.value) {
            _isPaused.value = false
            playCurrentChunk()
        }
    }

    fun stop() {
        _isActive.value = false
        _isPlaying.value = false
        _isPaused.value = false
        tts?.stop()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private fun chunkText(text: String): List<String> {
        val maxLength = TextToSpeech.getMaxSpeechInputLength() - 50
        val chunks = mutableListOf<String>()
        val paragraphs = text.split("\n")
        
        var currentChunk = StringBuilder()
        for (p in paragraphs) {
            if (currentChunk.length + p.length > maxLength) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                    currentChunk = StringBuilder()
                }
                
                if (p.length > maxLength) {
                    val sentences = p.split(Regex("(?<=[.!?])\\s+"))
                    for (s in sentences) {
                        if (currentChunk.length + s.length > maxLength) {
                            if (currentChunk.isNotEmpty()) {
                                chunks.add(currentChunk.toString())
                                currentChunk = StringBuilder()
                            }
                            // If sentence is still somehow too long, just chunk it strictly (fallback)
                            if (s.length > maxLength) {
                                chunks.add(s.substring(0, maxLength))
                            } else {
                                currentChunk.append(s).append(" ")
                            }
                        } else {
                            currentChunk.append(s).append(" ")
                        }
                    }
                } else {
                    currentChunk.append(p).append("\n")
                }
            } else {
                currentChunk.append(p).append("\n")
            }
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }
        return chunks.filter { it.isNotBlank() }
    }
}
