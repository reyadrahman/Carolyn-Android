package com.siddhantkushwaha.carolyn.ai

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager
import com.google.firebase.ml.custom.FirebaseCustomRemoteModel
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.siddhantkushwaha.carolyn.common.cleanText
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MessageClassifier private constructor(
    private val interpreter: Interpreter,
    private val metaData: Metadata
) {

    data class Metadata(
        val maxLen: Int,
        val classes: Array<String>,
        val index: HashMap<String, Float>
    )

    private val tag = "MessageClassifier"

    companion object {

        private val modelName = "message_classifier"
        private val metadataName = "meta.json"
        private val remoteModel = FirebaseCustomRemoteModel.Builder(modelName).build()

        /* --------------------------- FirebaseML model functions -------------------------------- */

        public fun downloadModel() {
            val conditions = FirebaseModelDownloadConditions.Builder().build()
            Tasks.await(FirebaseModelManager.getInstance().download(remoteModel, conditions))
        }

        public fun isModelDownloaded(): Boolean {
            return Tasks.await(FirebaseModelManager.getInstance().isModelDownloaded(remoteModel))
        }

        private fun getInterpreter(): Interpreter {
            val modelFile =
                Tasks.await(FirebaseModelManager.getInstance().getLatestModelFile(remoteModel))
            return Interpreter(modelFile)
        }

        private fun loadModel(forceDownload: Boolean = false): Interpreter {
            return if (isModelDownloaded() && !forceDownload) {
                getInterpreter()
            } else {
                downloadModel()
                getInterpreter()
            }
        }

        /* -------------------------------- Metadata functions ---------------------------------- */

        public fun downloadMetadata(context: Context) {
            val firebaseStorage = FirebaseStorage.getInstance()
            val metaData = File(context.getExternalFilesDir(null), metadataName)
            Tasks.await(firebaseStorage.getReference(metadataName).getFile(metaData))
        }

        public fun isMetadataDownloaded(context: Context): Boolean {
            return File(context.getExternalFilesDir(null), metadataName).exists()
        }

        private fun getMetaData(context: Context): Metadata {

            val maxLenAttr = "maxlen"
            val classesAttr = "classes"
            val indexAttr = "index"

            val gson = Gson()
            val metaDataFile = File(context.getExternalFilesDir(null), metadataName)
            val metaJson = gson.fromJson(metaDataFile.bufferedReader(), JsonObject::class.java)
            val maxLen = metaJson.getAsJsonPrimitive(maxLenAttr).asInt
            val classes =
                gson.fromJson(metaJson.getAsJsonArray(classesAttr), Array(0) { "" }.javaClass)
            val index = gson.fromJson(
                metaJson.getAsJsonObject(indexAttr),
                HashMap<String, Float>().javaClass
            )
            return Metadata(maxLen, classes, index)
        }

        private fun loadMetaData(context: Context, forceDownload: Boolean = false): Metadata {
            return if (isMetadataDownloaded(context) && !forceDownload) {
                getMetaData(context)
            } else {
                downloadMetadata(context)
                getMetaData(context)
            }
        }

        /* ----------------------------- get classifier object ---------------------------------- */

        public fun getInstance(context: Context): MessageClassifier? {
            var messageClassifier: MessageClassifier? = null
            try {

                val interpreter = loadModel()
                val metaData = loadMetaData(context)

                messageClassifier = MessageClassifier(interpreter, metaData)
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
            return messageClassifier
        }
    }

    public fun doClassification(uncleanedMessage: String): String {
        var body = cleanText(uncleanedMessage)
        body = body.replace("#", "0")

        val tokens = body.split(" ")
        val tokenToIndex = ArrayList<Float>()

        tokens.forEachIndexed { index, token ->
            if (index < metaData.maxLen) {
                tokenToIndex.add(metaData.index.getOrDefault(token, 0F))
            }
        }

        while (tokenToIndex.size < metaData.maxLen) {
            tokenToIndex.add(0F)
        }

        val input = ByteBuffer.allocateDirect(metaData.maxLen * 4).order(ByteOrder.nativeOrder())
        val output =
            ByteBuffer.allocateDirect(metaData.classes.size * 4).order(ByteOrder.nativeOrder())

        tokenToIndex.forEach { tokenVal -> input.putFloat(tokenVal) }
        interpreter.run(input, output)

        val probabilities = FloatArray(4)
        output.rewind()
        output.asFloatBuffer().get(probabilities)

        var prediction = 0
        for (i in probabilities.indices)
            if (probabilities[i] > probabilities[prediction])
                prediction = i

        return metaData.classes[prediction]
    }
}