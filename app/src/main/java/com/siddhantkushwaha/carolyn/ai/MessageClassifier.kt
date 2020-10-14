package com.siddhantkushwaha.carolyn.ai

import android.app.Activity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager
import com.google.firebase.ml.custom.FirebaseCustomRemoteModel
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.JsonObject
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

        private fun downloadModel(remoteModel: FirebaseCustomRemoteModel) {
            val conditions = FirebaseModelDownloadConditions.Builder().build()
            Tasks.await(FirebaseModelManager.getInstance().download(remoteModel, conditions))
        }

        private fun getInterpreter(remoteModel: FirebaseCustomRemoteModel): Interpreter {
            val modelFile = Tasks.await(FirebaseModelManager.getInstance().getLatestModelFile(remoteModel))
            return Interpreter(modelFile)
        }

        private fun loadModel(modelName: String, forceDownload: Boolean = false): Interpreter {
            val remoteModel = FirebaseCustomRemoteModel.Builder(modelName).build()
            val isModelDownloaded = Tasks.await(FirebaseModelManager.getInstance().isModelDownloaded(remoteModel))
            return if (isModelDownloaded && !forceDownload) {
                getInterpreter(remoteModel)
            } else {
                downloadModel(remoteModel)
                getInterpreter(remoteModel)
            }
        }

        private fun downloadMetadata(metadataName: String, localDirPath: File) {
            val firebaseStorage = FirebaseStorage.getInstance()
            val metaData = File(localDirPath, metadataName)
            Tasks.await(firebaseStorage.getReference(metadataName).getFile(metaData))
        }

        private fun getMetaData(metadataName: String, localDirPath: File): Metadata {

            val maxLenAttr = "maxlen"
            val classesAttr = "classes"
            val indexAttr = "index"

            val gson = Gson()
            val metaDataFile = File(localDirPath, metadataName)
            val metaJson = gson.fromJson(metaDataFile.bufferedReader(), JsonObject::class.java)
            val maxLen = metaJson.getAsJsonPrimitive(maxLenAttr).asInt
            val classes = gson.fromJson(metaJson.getAsJsonArray(classesAttr), Array(0) { "" }.javaClass)
            val index = gson.fromJson(metaJson.getAsJsonObject(indexAttr), HashMap<String, Float>().javaClass)
            return Metadata(maxLen, classes, index)
        }

        private fun loadMetaData(activity: Activity, metadataName: String, forceDownload: Boolean = false): Metadata {
            val localDirPath = activity.getExternalFilesDir(null)!!
            val metaDataFile = File(localDirPath, metadataName)
            return if (metaDataFile.exists() && !forceDownload) {
                getMetaData(metadataName, localDirPath)
            } else {
                downloadMetadata(metadataName, localDirPath)
                getMetaData(metadataName, localDirPath)
            }
        }

        fun getInstance(activity: Activity): MessageClassifier? {
            var messageClassifier: MessageClassifier? = null
            try {
                val modelName = "message_classifier"
                val interpreter = loadModel(modelName)

                val metadataName = "meta.json"
                val metaData = loadMetaData(activity, metadataName)

                messageClassifier = MessageClassifier(interpreter, metaData)
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
            return messageClassifier
        }
    }

    public fun doClassification(message: String): String {
        val body = message.replace("#", "0")
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
        val output = ByteBuffer.allocateDirect(metaData.classes.size * 4).order(ByteOrder.nativeOrder())

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