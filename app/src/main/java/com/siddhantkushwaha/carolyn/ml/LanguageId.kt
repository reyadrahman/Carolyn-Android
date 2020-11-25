package com.siddhantkushwaha.carolyn.ml

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification


class LanguageId {

    companion object {

        public fun getLanguage(body: String): String {
            try {
                val languageIdentifier = LanguageIdentification.getClient()
                return Tasks.await(languageIdentifier.identifyLanguage(body))
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
            return "und"
        }
    }
}