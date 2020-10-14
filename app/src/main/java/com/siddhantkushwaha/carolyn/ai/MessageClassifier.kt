package com.siddhantkushwaha.carolyn.ai

import android.app.Activity
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager
import com.google.firebase.ml.custom.*
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.siddhantkushwaha.carolyn.common.RealmUtil
import com.siddhantkushwaha.carolyn.common.cleanText
import com.siddhantkushwaha.carolyn.entity.Message
import java.io.File

class MessageClassifier(activity: Activity) {

    private val TAG = "MessageClassifier"

    private val remoteModel = FirebaseCustomRemoteModel.Builder("message_classifier").build()

    data class Metadata(val maxlen: Int, val classes: Array<String>, val index: HashMap<String, Float>)

    private val gson = Gson()
    private val realm = RealmUtil.getCustomRealmInstance(activity)
    private val firebaseStorage = FirebaseStorage.getInstance()
    private val localDirPath = activity.getExternalFilesDir(null)

    private lateinit var interpreter: FirebaseModelInterpreter
    private lateinit var metadata: Metadata

    private var loaded: Boolean = false

    init {
        loadAll()
    }

    private fun downloadModel(): Boolean {
        var downloaded = false
        val conditions = FirebaseModelDownloadConditions.Builder().build()
        try {
            Tasks.await(FirebaseModelManager.getInstance().download(remoteModel, conditions))
            downloaded = true
        } catch (exception: Exception) {
            Log.e(TAG, "Model download failed with below exception.")
            exception.printStackTrace()
        }
        return downloaded
    }

    private fun setInterpreter() {
        val options = FirebaseModelInterpreterOptions.Builder(remoteModel).build()
        val interpreterL = FirebaseModelInterpreter.getInstance(options)
        if (interpreterL != null)
            interpreter = interpreterL
        else
            throw Exception("Could not create interpreter object.")
    }

    private fun loadModel(forceDownload: Boolean = false) {
        try {
            val isModelDownloaded = Tasks.await(FirebaseModelManager.getInstance().isModelDownloaded(remoteModel))
            if (isModelDownloaded && !forceDownload)
                setInterpreter()
            else if (downloadModel())
                setInterpreter()
        } catch (exception: Exception) {
            Log.e(TAG, "Exception in loadModel function.")
            exception.printStackTrace()
        }
    }

    private fun downloadMetadata(): Boolean {
        var downloaded = false
        try {
            val metaData = File(localDirPath, "meta.json")
            Tasks.await(firebaseStorage.getReference("meta.json").getFile(metaData))
            downloaded = true
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
        return downloaded
    }

    private fun setMetaData() {
        try {
            val metaDataFile = File(localDirPath, "meta.json")
            val metaJson = gson.fromJson(metaDataFile.bufferedReader(), JsonObject::class.java)
            val maxLen = metaJson.getAsJsonPrimitive("maxlen").asInt
            val classes = gson.fromJson(metaJson.getAsJsonArray("classes"), Array(0) { _ -> "" }.javaClass)
            val index = gson.fromJson(metaJson.getAsJsonObject("index"), HashMap<String, Float>().javaClass)
            metadata = Metadata(maxLen, classes, index)
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }

    private fun loadMetaData(forceDownload: Boolean = false) {
        val metaData = File(localDirPath, "meta.json")
        if (metaData.exists() && !forceDownload)
            setMetaData()
        else if (downloadMetadata())
            setMetaData()
    }

    private fun loadAll() {
        try {
            loadModel()
            loadMetaData()
            loaded = false
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to initiate message classfier.")
            exception.printStackTrace()
        }
    }

    private fun doClassification(messages: Array<String>): Array<String>? {

        val tokenizedInputs = messages.map { input ->

            val tokenizedInput: List<Float> = input.replace("#", "0").split(" ").map { word -> metadata.index.getOrDefault(word, 0F).toFloat() }

            val tokenList = ArrayList<Float>()
            tokenizedInput.forEachIndexed { i, fl ->
                if (i < metadata.maxlen) {
                    tokenList.add(fl)
                }
            }
            while (tokenList.size < metadata.maxlen) {
                tokenList.add(0F)
            }

            tokenList.toFloatArray()
        }.toTypedArray()

        val inputOutputOptions = FirebaseModelInputOutputOptions
            .Builder()
            .setInputFormat(
                0,
                FirebaseModelDataType.FLOAT32,
                intArrayOf(tokenizedInputs.size, metadata.maxlen)
            )
            .setOutputFormat(
                0,
                FirebaseModelDataType.FLOAT32,
                intArrayOf(tokenizedInputs.size, metadata.classes.size)
            ).build()

        val modelInput = FirebaseModelInputs.Builder().add((tokenizedInputs)).build()
        interpreter.run(modelInput, inputOutputOptions)

        var classes: Array<String>? = null

        val task = interpreter.run(modelInput, inputOutputOptions)
        try {
            val result = Tasks.await(task)
            val output = result.getOutput<Array<FloatArray>>(0)
            val predictions = ArrayList<String>()
            output.forEach { probabilities -> predictions.add(metadata.classes[probabilities.indexOfFirst { it == probabilities.maxOrNull()!! }]) }
            classes = predictions.toTypedArray()
        } catch (exception: Exception) {
            exception.printStackTrace()
        }

        return classes
    }

    private fun classifyMessageAndSave(messageId: String, body: String) {
        val cleanedBody = cleanText(body)
        val skipMessage = cleanedBody.count { it == '#' } / cleanedBody.length.toFloat() > 0.5
        if (!skipMessage) {
            val classes = doClassification(arrayOf(cleanedBody))
            if (classes != null) {
                realm.executeTransaction { realmT ->
                    val messageL = realmT.where(Message::class.java).equalTo("id", messageId).findFirst()
                    if (messageL != null) {
                        messageL.type = classes[0]
                        realmT.insertOrUpdate(messageL)
                    }
                }
            }
        }
    }

    public fun classify(messageId: String, body: String) {
        if (loaded) {
            classifyMessageAndSave(messageId, body)
        } else {
            loadAll()
            if (loaded) classifyMessageAndSave(messageId, body)
        }
    }
}