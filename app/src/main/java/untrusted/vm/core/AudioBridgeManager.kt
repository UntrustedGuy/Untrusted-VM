package untrusted.vm.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bi-directional Audio Bridge Subsystem.
 * Equivalent to VMOS Pro's NativeAudioManagerUtil / NativeAudioUtil.
 * Handles PCM audio routing between the host system and the guest VM container:
 * 1. Audio Playback: reads guest PCM stream from virtual audio FIFO/pipe and plays via host AudioTrack.
 * 2. Microphone Capture: records host microphone via AudioRecord and feeds into guest audio input FIFO.
 */
class AudioBridgeManager(
    private val context: Context,
    private val vmId: Int,
    private val filesystem: VmFilesystem
) {
    companion object {
        private const val TAG = "AudioBridgeManager"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_STEREO
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private val isPlaying = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)
    private var isMuted = false
    private var playbackThread: Thread? = null
    private var recordThread: Thread? = null

    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null

    val guestAudioOutPipe: File
        get() = filesystem.resolveGuestFile("/dev/snd/pcmC0D0p")

    val guestAudioInPipe: File
        get() = filesystem.resolveGuestFile("/dev/snd/pcmC0D0c")

    fun start() {
        if (isPlaying.get()) return

        // Ensure virtual /dev/snd directory exists
        guestAudioOutPipe.parentFile?.mkdirs()
        if (!guestAudioOutPipe.exists()) {
            try {
                guestAudioOutPipe.createNewFile()
            } catch (e: Exception) {
                Log.w(TAG, "Could not create guest audio out file: ${e.message}")
            }
        }

        startPlayback()
    }

    fun stop() {
        stopPlayback()
        stopRecording()
    }

    fun setMuted(muted: Boolean) {
        this.isMuted = muted
        audioTrack?.setVolume(if (muted) 0f else 1.0f)
    }

    private fun startPlayback() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_OUT,
            AUDIO_ENCODING
        )

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .setEncoding(AUDIO_ENCODING)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isPlaying.set(true)

            playbackThread = Thread({
                val buffer = ByteArray(minBufferSize)
                var fileOffset = 0L

                while (isPlaying.get()) {
                    try {
                        if (guestAudioOutPipe.exists() && guestAudioOutPipe.length() > fileOffset) {
                            val raf = RandomAccessFile(guestAudioOutPipe, "r")
                            raf.seek(fileOffset)
                            val bytesRead = raf.read(buffer)
                            if (bytesRead > 0) {
                                fileOffset += bytesRead
                                if (!isMuted) {
                                    audioTrack?.write(buffer, 0, bytesRead)
                                }
                            }
                            raf.close()

                            // If pipe file grows too large (> 10MB), truncate to prevent disk ballooning
                            if (guestAudioOutPipe.length() > 10 * 1024 * 1024) {
                                RandomAccessFile(guestAudioOutPipe, "rw").use { it.setLength(0) }
                                fileOffset = 0L
                            }
                        } else {
                            Thread.sleep(20)
                        }
                    } catch (e: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        // Sleep briefly on I/O contention
                        try { Thread.sleep(50) } catch (_: Exception) {}
                    }
                }
            }, "UntrustedVM-AudioOut-$vmId").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack for VM #$vmId", e)
        }
    }

    private fun stopPlayback() {
        isPlaying.set(false)
        playbackThread?.interrupt()
        playbackThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack: ${e.message}")
        }
        audioTrack = null
    }

    fun startMicrophoneRecording() {
        if (isRecording.get()) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_IN,
            AUDIO_ENCODING
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_IN,
                AUDIO_ENCODING,
                minBufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            recordThread = Thread({
                val buffer = ByteArray(minBufferSize)
                while (isRecording.get()) {
                    try {
                        val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readBytes > 0 && guestAudioInPipe.exists()) {
                            FileOutputStream(guestAudioInPipe, true).use { fos ->
                                fos.write(buffer, 0, readBytes)
                            }
                        }
                    } catch (e: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        try { Thread.sleep(50) } catch (_: Exception) {}
                    }
                }
            }, "UntrustedVM-AudioIn-$vmId").apply {
                isDaemon = true
                start()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Microphone permission not granted for VM #$vmId audio recording")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting microphone recording", e)
        }
    }

    fun stopRecording() {
        isRecording.set(false)
        recordThread?.interrupt()
        recordThread = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null
    }
}
