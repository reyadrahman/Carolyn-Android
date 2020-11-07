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
import java.lang.Integer.min
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MessageClassifier {

    data class Metadata(
        val maxLen: Int,
        val classes: Array<String>,
        val index: HashMap<String, Float>
    )

    private val tag = "MessageClassifier"

    companion object {

        private val modelName = "message_classifier"
        private val metadataName = "meta.json"


        /* --------------------------- FirebaseML model functions -------------------------------- */

        private fun downloadModel() {
            val conditions = FirebaseModelDownloadConditions.Builder().build()
            Tasks.await(
                FirebaseModelManager.getInstance()
                    .download(FirebaseCustomRemoteModel.Builder(modelName).build(), conditions)
            )
        }

        private fun isModelDownloaded(): Boolean {
            return Tasks.await(
                FirebaseModelManager.getInstance()
                    .isModelDownloaded(FirebaseCustomRemoteModel.Builder(modelName).build())
            )
        }

        private fun getInterpreter(): Interpreter {
            val modelFile = Tasks.await(
                FirebaseModelManager.getInstance()
                    .getLatestModelFile(FirebaseCustomRemoteModel.Builder(modelName).build())
            )
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

        private fun downloadMetadata(context: Context) {
            val firebaseStorage = FirebaseStorage.getInstance()
            val metaData = File(context.getExternalFilesDir(null), metadataName)
            Tasks.await(firebaseStorage.getReference(metadataName).getFile(metaData))
        }

        private fun isMetadataDownloaded(context: Context): Boolean {
            val file = File(context.getExternalFilesDir(null), metadataName)
            return file.exists()
        }

        private fun getMetaData(context: Context): Metadata {

            val maxLenAttr = "maxlen"
            val classesAttr = "classes"
            val indexAttr = "index"

            val gson = Gson()
            val metaDataFile = File(context.getExternalFilesDir(null), metadataName)
            val buffReader = metaDataFile.bufferedReader()
            val metaJson = gson.fromJson(buffReader, JsonObject::class.java)
                ?: throw Exception("Couldn't parse meta-file.")
            buffReader.close()
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

        /* ------------------------------------ Classifier -------------------------------------- */

        public fun doClassification(
            context: Context,
            uncleanedMessage: String,
            skipIfNotDownloaded: Boolean = true
        ): String? {
            try {
                if (skipIfNotDownloaded && !(isModelDownloaded() && isMetadataDownloaded(context)))
                    return null

                val interpreter = loadModel()
                val metaData = loadMetaData(context)

                var body = cleanText(uncleanedMessage)
                body = body.replace("#", "0")

                val tokens = body.split(" ")
                val tokenToIndex = ArrayList<Float>()

                tokens.forEachIndexed { index, token ->
                    if (index < metaData.maxLen) {
                        var idx = metaData.index.getOrDefault(token, 0F)

                        /*
                            ----- Hack Alert -----
                            Model keeps crashing for higher values for reason
                            We may have to live with this stupid hack
                        */
                        if(idx > 3000F)
                            idx = 1F

                        tokenToIndex.add(idx)
                    }
                }

                while (tokenToIndex.size < metaData.maxLen) {
                    tokenToIndex.add(0F)
                }

                val input =
                    ByteBuffer.allocateDirect(metaData.maxLen * 4).order(ByteOrder.nativeOrder())
                val output = ByteBuffer.allocateDirect(metaData.classes.size * 4)
                    .order(ByteOrder.nativeOrder())

                tokenToIndex.forEach { tokenVal ->
                    input.putFloat(tokenVal)
                }

                interpreter.run(input, output)
                interpreter.close()

                val probabilities = FloatArray(metaData.classes.size)
                output.rewind()
                output.asFloatBuffer().get(probabilities)

                var prediction = 0
                for (i in probabilities.indices)
                    if (probabilities[i] > probabilities[prediction])
                        prediction = i

                return metaData.classes[prediction]

            } catch (exception: Exception) {
                exception.printStackTrace()
            }

            return null
        }
    }
}