package com.siddhantkushwaha.carolyn.ai

import android.app.Activity
import android.util.Log
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

class MessageClassifier(private val activity: Activity) {

    private val TAG = "MessageClassifier"

    data class Metadata(
        val maxlen: Int,
        val classes: Array<String>,
        val index: HashMap<String, Float>
    )

    private val gson = Gson()
    private val firebaseStorage = FirebaseStorage.getInstance()
    private val remoteModel = FirebaseCustomRemoteModel.Builder("message_classifier").build()
    private val localDirPath = activity.getExternalFilesDir(null)

    private fun downloadModel(success: () -> Unit, fail: () -> Unit) {
        val conditions = FirebaseModelDownloadConditions.Builder().build()
        FirebaseModelManager.getInstance().download(remoteModel, conditions)
            .addOnSuccessListener {
                Log.i(TAG, "Message classifier downloaded successfully.")
                success()
            }
            .addOnFailureListener {
                Log.i(TAG, "Message classifier downloaded failed.")
                it.printStackTrace()
                fail()
            }
    }

    private fun getInterpreter(): FirebaseModelInterpreter {
        val options = FirebaseModelInterpreterOptions.Builder(remoteModel).build()
        return FirebaseModelInterpreter.getInstance(options)!!

    }

    private fun loadModel(
        ifLoaded: (FirebaseModelInterpreter) -> Unit,
        ifNotLoaded: () -> Unit
    ) {
        FirebaseModelManager.getInstance().isModelDownloaded(remoteModel)
            .addOnSuccessListener { isDownloaded ->
                if (isDownloaded) {
                    Log.i(TAG, "Messaged classified model is already downloaded.")
                    ifLoaded(getInterpreter())
                } else {
                    Log.i(TAG, "Messaged classified model has not been downloaded yet.")
                    downloadModel(
                        success = { ifLoaded(getInterpreter()) },
                        fail = { ifNotLoaded() }
                    )
                }
            }
            .addOnFailureListener {
                ifNotLoaded()
                it.printStackTrace()
            }
    }

    private fun downloadMetadata(success: () -> Unit, fail: () -> Unit) {
        val metaData = File(localDirPath, "meta.json")
        firebaseStorage.getReference("meta.json").getFile(metaData).addOnSuccessListener {
            Log.i(TAG, "Metadata downloaded successfully.")
            success()
        }.addOnFailureListener {
            Log.i(TAG, "Metadata download failed.")
            fail()
        }
    }

    private fun parseMetaData(): Metadata {
        val metaData = File(localDirPath, "meta.json")
        val metaJson = gson.fromJson(metaData.bufferedReader(), JsonObject::class.java)

        val maxlen = metaJson.getAsJsonPrimitive("maxlen").asInt

        val classes =
            gson.fromJson(metaJson.getAsJsonArray("classes"), Array(0) { _ -> "" }.javaClass)
        // Log.i(TAG, "Classes predicted by model - ")
        // classes.forEachIndexed { index, s -> Log.i(TAG, "Class: $index - $s") }

        val index =
            gson.fromJson(metaJson.getAsJsonObject("index"), HashMap<String, Float>().javaClass)
        // index.forEach { (s, fl) -> Log.i(TAG, "$s - $fl") }
        return Metadata(maxlen, classes, index)
    }

    private fun loadMetaData(ifLoaded: (Metadata) -> Unit, ifNotLoaded: () -> Unit) {
        val metaData = File(localDirPath, "meta.json")
        if (metaData.exists()) {
            Log.i(TAG, "Metadata already exists.")
            val parsedMetaData = parseMetaData()
            ifLoaded(parsedMetaData)
        } else {
            Log.i(TAG, "Metadata not found.")
            downloadMetadata(
                success = {
                    val parsedMetaData = parseMetaData()
                    ifLoaded(parsedMetaData)
                },
                fail = { ifNotLoaded() }
            )
        }
    }

    private fun startClassification(interpreter: FirebaseModelInterpreter, metadata: Metadata, messages: Array<String>, callback: (Array<String>) -> Unit) {

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
            .addOnSuccessListener { result ->
                Log.i("Message_Classifier", "Messages interpreted.")
                val output = result.getOutput<Array<FloatArray>>(0)
                val predictions = ArrayList<String>()
                output.forEach { probabilities ->
                    predictions.add(metadata.classes[probabilities.indexOf(probabilities.max()!!)])
                }
                callback(predictions.toTypedArray())
            }
            .addOnFailureListener { e ->
                Log.i("Message_Classifier", "Failed to run interpretations.")
                e.printStackTrace()
            }
    }

    private fun classifyMessage(messageId: String, body: String) {
        loadModel(
            ifLoaded = { interpreter ->
                loadMetaData(
                    ifLoaded = { metadata ->
                        val cleanedBody = cleanText(body)
                        if (cleanedBody.count { it == '#' } / cleanedBody.length.toFloat() < 0.5) {
                            startClassification(interpreter, metadata, arrayOf(cleanedBody)) { classes ->

                                Log.i(TAG, "$cleanedBody - ${classes[0]}")
                                RealmUtil.getCustomRealmInstance(activity).executeTransaction { realmT ->
                                    val messageL = realmT.where(Message::class.java).equalTo("id", messageId).findFirst()
                                    if (messageL != null) {
                                        messageL.type = classes[0]
                                        realmT.insertOrUpdate(messageL)
                                    }
                                }

                            }
                        }
                    },
                    ifNotLoaded = {}
                )
            },
            ifNotLoaded = {}
        )
    }

    public fun interpretMessages() {
        RealmUtil.getCustomRealmInstance(activity).where(Message::class.java).isNull("type").findAll().forEach { message ->
            classifyMessage(message.id!!, message.body!!)
        }
    }
}