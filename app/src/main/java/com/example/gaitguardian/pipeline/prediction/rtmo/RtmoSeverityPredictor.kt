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

class RtmoSeverityPredictor(
    private val context: Context
) {
    companion object {
        private const val TAG = "RtmoSeverityPredictor"
        private const val MODEL_FILE = "mlp_severity.onnx"

        private val BINARY_LABELS = listOf("Normal", "Impaired")
        private val MULTICLASS_LABELS = listOf("Normal", "Slight", "Mild", "Moderate", "Severe")
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
            Log.e(TAG, "Failed to initialize RTMO severity MLP", e)
            false
        }
    }

    suspend fun predict(features: RtmoSeverityFeatures): RtmoSeverityPredictionResult = withContext(Dispatchers.Default) {
        val ortSession = session ?: return@withContext RtmoSeverityPredictionResult(
            success = false,
            featureNames = features.featureNames,
            featureVector = features.featureVector,
            errorMessage = "RTMO severity MLP is not initialized"
        )
        val env = ortEnvironment ?: return@withContext RtmoSeverityPredictionResult(
            success = false,
            featureNames = features.featureNames,
            featureVector = features.featureVector,
            errorMessage = "ONNX environment is not initialized"
        )

        val inputEntries = ortSession.inputInfo.entries.toList()
        if (inputEntries.isEmpty()) {
            return@withContext RtmoSeverityPredictionResult(
                success = false,
                featureNames = features.featureNames,
                featureVector = features.featureVector,
                errorMessage = "Severity MLP has no inputs"
            )
        }

        val mainInputEntry = inputEntries.first()
        val mainInputName = mainInputEntry.key
        val mainTensorInfo = mainInputEntry.value.info as? TensorInfo
            ?: return@withContext RtmoSeverityPredictionResult(
                success = false,
                featureNames = features.featureNames,
                featureVector = features.featureVector,
                errorMessage = "Main MLP input is not a tensor"
            )

        val inputShape = resolveVectorInputShape(mainTensorInfo, features.featureVector.size)
        val tensors = linkedMapOf<String, OnnxTensor>()
        try {
            tensors[mainInputName] = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(features.featureVector),
                inputShape
            )

            inputEntries.drop(1).forEach { entry ->
                val tensorInfo = entry.value.info as? TensorInfo ?: return@forEach
                tensors[entry.key] = createZeroTensor(env, tensorInfo, features.featureVector.size)
            }

            val outputEntries = ortSession.outputInfo.entries.toList()
            val results = ortSession.run(tensors)
            try {
                val outputValues = outputEntries.indices.map { index -> results.get(index).value }
                val parsedOutputs = parseOutputs(outputValues)
                return@withContext RtmoSeverityPredictionResult(
                    success = parsedOutputs.severityLabel != null || parsedOutputs.predictedClassIndex != null,
                    severityLabel = parsedOutputs.severityLabel,
                    predictedClassIndex = parsedOutputs.predictedClassIndex,
                    classProbabilities = parsedOutputs.classProbabilities,
                    featureNames = features.featureNames,
                    featureVector = features.featureVector,
                    inputName = mainInputName,
                    outputNames = outputEntries.map { it.key },
                    inputShape = inputShape,
                    outputShapes = outputValues.map { inferOutputShape(it) },
                    errorMessage = if (parsedOutputs.severityLabel == null && parsedOutputs.predictedClassIndex == null) {
                        "Unsupported MLP output format"
                    } else {
                        null
                    }
                )
            } finally {
                results.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "RTMO severity prediction failed", e)
            return@withContext RtmoSeverityPredictionResult(
                success = false,
                featureNames = features.featureNames,
                featureVector = features.featureVector,
                inputName = mainInputName,
                inputShape = inputShape,
                errorMessage = e.message ?: "Unknown RTMO severity inference error"
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

    private fun resolveVectorInputShape(info: TensorInfo, featureCount: Int): LongArray {
        val shape = info.shape.clone()
        return when (shape.size) {
            2 -> longArrayOf(
                resolveDynamicDim(shape[0], 1L),
                resolveDynamicDim(shape[1], featureCount.toLong())
            )
            1 -> longArrayOf(resolveDynamicDim(shape[0], featureCount.toLong()))
            else -> LongArray(shape.size) { index ->
                when {
                    shape[index] > 0L -> shape[index]
                    index == shape.lastIndex -> featureCount.toLong()
                    else -> 1L
                }
            }
        }
    }

    private fun createZeroTensor(
        env: OrtEnvironment,
        tensorInfo: TensorInfo,
        featureCount: Int
    ): OnnxTensor {
        val shape = tensorInfo.shape.mapIndexed { index, dim ->
            when {
                dim > 0L -> dim
                index == tensorInfo.shape.lastIndex -> featureCount.toLong()
                else -> 1L
            }
        }.toLongArray()

        val elementCount = shape.fold(1L) { acc, dim -> acc * dim }.toInt()
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(elementCount)), shape)
    }

    private data class ParsedOutputs(
        val severityLabel: String? = null,
        val predictedClassIndex: Int? = null,
        val classProbabilities: FloatArray = FloatArray(0)
    )

    private fun parseOutputs(outputValues: List<Any?>): ParsedOutputs {
        var severityLabel: String? = null
        var predictedClassIndex: Int? = null
        var classProbabilities = FloatArray(0)

        outputValues.forEach { value ->
            when (value) {
                is LongArray -> {
                    if (value.isNotEmpty()) {
                        predictedClassIndex = value[0].toInt()
                    }
                }
                is IntArray -> {
                    if (value.isNotEmpty()) {
                        predictedClassIndex = value[0]
                    }
                }
                is FloatArray -> {
                    if (classProbabilities.isEmpty()) {
                        classProbabilities = value.copyOf()
                    }
                }
                is DoubleArray -> {
                    if (classProbabilities.isEmpty()) {
                        classProbabilities = FloatArray(value.size) { index -> value[index].toFloat() }
                    }
                }
                is Array<*> -> {
                    val first = value.firstOrNull()
                    when (first) {
                        is LongArray -> {
                            if (first.isNotEmpty()) {
                                predictedClassIndex = first[0].toInt()
                            }
                        }
                        is IntArray -> {
                            if (first.isNotEmpty()) {
                                predictedClassIndex = first[0]
                            }
                        }
                        is FloatArray -> {
                            if (classProbabilities.isEmpty()) {
                                classProbabilities = first.copyOf()
                            }
                        }
                        is DoubleArray -> {
                            if (classProbabilities.isEmpty()) {
                                classProbabilities = FloatArray(first.size) { index -> first[index].toFloat() }
                            }
                        }
                        is String -> {
                            if (severityLabel == null) {
                                severityLabel = first
                            }
                        }
                    }
                }
            }
        }

        if (predictedClassIndex == null && classProbabilities.isNotEmpty()) {
            predictedClassIndex = classProbabilities.indices.maxByOrNull { index -> classProbabilities[index] }
        }

        if (severityLabel == null) {
            predictedClassIndex?.let { classIndex ->
                severityLabel = inferSeverityLabel(classIndex, classProbabilities.size)
            }
        }

        return ParsedOutputs(
            severityLabel = severityLabel,
            predictedClassIndex = predictedClassIndex,
            classProbabilities = classProbabilities
        )
    }

    private fun inferSeverityLabel(predictedClassIndex: Int, probabilityCount: Int): String {
        val labelSet = when (probabilityCount) {
            2 -> BINARY_LABELS
            5 -> MULTICLASS_LABELS
            else -> emptyList()
        }
        return labelSet.getOrElse(predictedClassIndex) { "Class-$predictedClassIndex" }
    }

    private fun inferOutputShape(value: Any?): LongArray? {
        return when (value) {
            is Array<*> -> {
                if (value.isEmpty()) {
                    longArrayOf(0)
                } else {
                    when (val first = value[0]) {
                        is LongArray -> longArrayOf(value.size.toLong(), first.size.toLong())
                        is IntArray -> longArrayOf(value.size.toLong(), first.size.toLong())
                        is FloatArray -> longArrayOf(value.size.toLong(), first.size.toLong())
                        is DoubleArray -> longArrayOf(value.size.toLong(), first.size.toLong())
                        else -> longArrayOf(value.size.toLong())
                    }
                }
            }
            is LongArray -> longArrayOf(value.size.toLong())
            is IntArray -> longArrayOf(value.size.toLong())
            is FloatArray -> longArrayOf(value.size.toLong())
            is DoubleArray -> longArrayOf(value.size.toLong())
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
