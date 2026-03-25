package com.example.gaitguardian.pipeline.prediction.rtmo

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RtmoPhasePredictor(
    private val context: Context
) {
    companion object {
        private const val TAG = "RtmoPhasePredictor"
        private const val MODEL_FILE = "lstm_phases.onnx"
        val DEFAULT_PHASE_LABELS = listOf(
            "Sit-To-Stand",
            "Walk-From-Chair",
            "Turn-First",
            "Walk-To-Chair",
            "Stand-To-Sit"
        )
    }

    private var ortEnvironment: OrtEnvironment? = null
    private var session: OrtSession? = null

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (session != null) {
                return@withContext true
            }
            ortEnvironment = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(MODEL_FILE).readBytes()
            session = ortEnvironment!!.createSession(modelBytes)
            logModelMetadata(session!!)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LSTM phase model", e)
            false
        }
    }

    suspend fun predict(sequence: RtmoNormalizedSequence): RtmoPhasePredictionResult = withContext(Dispatchers.Default) {
        val ortSession = session ?: return@withContext RtmoPhasePredictionResult(
            success = false,
            errorMessage = "LSTM phase model is not initialized"
        )
        val env = ortEnvironment ?: return@withContext RtmoPhasePredictionResult(
            success = false,
            errorMessage = "ONNX environment is not initialized"
        )

        val inputEntries = ortSession.inputInfo.entries.toList()
        if (inputEntries.isEmpty()) {
            return@withContext RtmoPhasePredictionResult(
                success = false,
                errorMessage = "LSTM model has no inputs"
            )
        }

        val mainInputEntry = inputEntries.first()
        val mainInputName = mainInputEntry.key
        val mainTensorInfo = (mainInputEntry.value.info as? TensorInfo)
            ?: return@withContext RtmoPhasePredictionResult(
                success = false,
                errorMessage = "Main LSTM input is not a tensor"
            )

        val featureCount = sequence.jointCount * sequence.dimensionsPerJoint
        val inputShape = resolveSequenceInputShape(mainTensorInfo, sequence.frameCount, featureCount)
        val flattened = FloatArray(sequence.frameCount * featureCount)
        var cursor = 0
        for (frameIndex in 0 until sequence.frameCount) {
            for (jointIndex in 0 until sequence.jointCount) {
                flattened[cursor++] = sequence.coordinates[frameIndex][jointIndex][0]
                flattened[cursor++] = sequence.coordinates[frameIndex][jointIndex][1]
            }
        }

        val tensors = linkedMapOf<String, OnnxTensor>()
        try {
            tensors[mainInputName] = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(flattened),
                inputShape
            )

            inputEntries.drop(1).forEach { entry ->
                val tensorInfo = entry.value.info as? TensorInfo ?: return@forEach
                tensors[entry.key] = createZeroTensor(env, tensorInfo, sequence.frameCount, featureCount)
            }

            val results = ortSession.run(tensors)
            try {
                val parsed = parsePrimaryOutput(results, sequence.fps)
                return@withContext parsed.copy(
                    inputName = mainInputName,
                    outputName = ortSession.outputInfo.keys.firstOrNull(),
                    inputShape = inputShape,
                    outputShape = inferOutputShape(results.get(0).value),
                    labels = if (parsed.labels.isEmpty()) DEFAULT_PHASE_LABELS else parsed.labels
                )
            } finally {
                results.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "LSTM phase prediction failed", e)
            return@withContext RtmoPhasePredictionResult(
                success = false,
                inputName = mainInputName,
                inputShape = inputShape,
                errorMessage = e.message ?: "Unknown LSTM inference error"
            )
        } finally {
            tensors.values.forEach { tensor ->
                try {
                    tensor.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun cleanup() {
        try {
            session?.close()
            ortEnvironment?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup warning", e)
        } finally {
            session = null
            ortEnvironment = null
        }
    }

    private fun resolveSequenceInputShape(info: TensorInfo, frameCount: Int, featureCount: Int): LongArray {
        val shape = info.shape.clone()
        return when (shape.size) {
            3 -> longArrayOf(
                resolveDynamicDim(shape[0], 1L),
                resolveDynamicDim(shape[1], frameCount.toLong()),
                resolveDynamicDim(shape[2], featureCount.toLong())
            )
            2 -> longArrayOf(
                resolveDynamicDim(shape[0], frameCount.toLong()),
                resolveDynamicDim(shape[1], featureCount.toLong())
            )
            else -> LongArray(shape.size) { index ->
                resolveDynamicDim(shape[index], 1L)
            }
        }
    }

    private fun createZeroTensor(
        env: OrtEnvironment,
        tensorInfo: TensorInfo,
        frameCount: Int,
        featureCount: Int
    ): OnnxTensor {
        val shape = tensorInfo.shape.mapIndexed { index, dim ->
            when {
                dim > 0L -> dim
                tensorInfo.shape.size == 3 && index == 1 -> frameCount.toLong()
                tensorInfo.shape.size >= 2 && index == tensorInfo.shape.size - 1 -> featureCount.toLong()
                else -> 1L
            }
        }.toLongArray()

        val elementCount = shape.fold(1L) { acc, dim -> acc * dim }.toInt()
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(elementCount)), shape)
    }

    private fun parsePrimaryOutput(
        results: OrtSession.Result,
        fps: Float
    ): RtmoPhasePredictionResult {
        val outputValue = results.get(0).value
        val logits = when (outputValue) {
            is Array<*> -> extractLogits(outputValue)
            else -> emptyList()
        }

        if (logits.isEmpty()) {
            return RtmoPhasePredictionResult(
                success = false,
                errorMessage = "Unsupported LSTM output format: ${outputValue?.javaClass?.simpleName}"
            )
        }

        val labels = DEFAULT_PHASE_LABELS
        val frameClassIndices = logits.map { frameLogits ->
            frameLogits.indices.maxByOrNull { frameLogits[it] } ?: 0
        }
        val frameLabels = frameClassIndices.map { classIndex ->
            labels.getOrElse(classIndex) { "Class-$classIndex" }
        }
        val phaseDurations = frameLabels.groupingBy { it }
            .eachCount()
            .mapValues { (_, count) -> count.toFloat() / fps.coerceAtLeast(1e-6f) }
        val orderedDurations = FloatArray(labels.size) { index ->
            phaseDurations[labels[index]] ?: 0f
        }

        return RtmoPhasePredictionResult(
            success = true,
            labels = labels,
            frameLabels = frameLabels,
            frameClassIndices = frameClassIndices,
            phaseDurationsSec = phaseDurations,
            orderedPhaseDurationsSec = orderedDurations
        )
    }

    private fun extractLogits(output: Array<*>): List<FloatArray> {
        if (output.isEmpty()) {
            return emptyList()
        }

        val first = output[0]
        return when (first) {
            is Array<*> -> {
                val nested = first
                if (nested.isNotEmpty() && nested[0] is FloatArray) {
                    nested.map { it as FloatArray }
                } else {
                    output.filterIsInstance<FloatArray>()
                }
            }
            is FloatArray -> output.filterIsInstance<FloatArray>()
            else -> emptyList()
        }
    }

    private fun inferOutputShape(value: Any?): LongArray? {
        return when (value) {
            is Array<*> -> {
                if (value.isEmpty()) {
                    longArrayOf(0)
                } else {
                    when (val first = value[0]) {
                        is Array<*> -> {
                            val inner = first.firstOrNull()
                            when (inner) {
                                is FloatArray -> longArrayOf(value.size.toLong(), first.size.toLong(), inner.size.toLong())
                                else -> longArrayOf(value.size.toLong(), first.size.toLong())
                            }
                        }
                        is FloatArray -> longArrayOf(value.size.toLong(), first.size.toLong())
                        else -> longArrayOf(value.size.toLong())
                    }
                }
            }
            is FloatArray -> longArrayOf(value.size.toLong())
            else -> null
        }
    }

    private fun resolveDynamicDim(dim: Long, fallback: Long): Long {
        return if (dim <= 0L) fallback else dim
    }

    private fun logModelMetadata(session: OrtSession) {
        session.inputInfo.forEach { (name, nodeInfo) ->
            val tensorInfo = nodeInfo.info as? TensorInfo
            Log.e(TAG, "input name=$name shape=${tensorInfo?.shape?.contentToString()} type=${tensorInfo?.type}")
        }
        session.outputInfo.forEach { (name, nodeInfo) ->
            val tensorInfo = nodeInfo.info as? TensorInfo
            Log.e(TAG, "output name=$name shape=${tensorInfo?.shape?.contentToString()} type=${tensorInfo?.type}")
        }
    }
}
