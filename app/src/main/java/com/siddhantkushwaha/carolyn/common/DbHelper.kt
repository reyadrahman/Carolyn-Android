package com.siddhantkushwaha.carolyn.common

import android.content.Context
import com.siddhantkushwaha.carolyn.common.util.CommonUtil
import com.siddhantkushwaha.carolyn.entity.*
import io.realm.Realm


object DbHelper {

    private const val unsavedNumberClassificationRuleAttrName = "UNKNOWN_NUMBER_CLASSIFICATION"
    private const val senderSupportReplyRuleAttrName = "SENDER_SUPPORTS_REPLIES"

    public fun setUnsavedNumberClassificationRule(context: Context, isChecked: Boolean) {
        val attrVal = if (isChecked) "1" else "0"
        GlobalParam.setGlobalParam(context, unsavedNumberClassificationRuleAttrName, attrVal)
    }

    public fun getUnsavedNumberClassificationRule(context: Context): Boolean {
        val globalParam =
            GlobalParam.getGlobalParam(context, unsavedNumberClassificationRuleAttrName)
        return globalParam ?: "1" == "1"
    }

    public fun setSenderSupportsReplyRule(context: Context, isChecked: Boolean) {
        val attrVal = if (isChecked) "1" else "0"
        GlobalParam.setGlobalParam(context, senderSupportReplyRuleAttrName, attrVal)
    }

    public fun getSenderSupportsReplyRule(context: Context): Boolean {
        val globalParam =
            GlobalParam.getGlobalParam(context, senderSupportReplyRuleAttrName)
        return globalParam ?: "1" == "1"
    }

    public fun getMessageId(timestampMillis: Long, body: String): String {
        return CommonUtil.getHash("${timestampMillis}, $body")
    }

    /* Call create messages from withing a transaction only */

    public fun createMessageObject(realm: Realm, messageId: String): Message {
        return realm.createObject(Message::class.java, messageId)
            ?: throw Exception("Failed to create message object.")
    }

    public fun createThreadObject(realm: Realm, user2: String): MessageThread {
        return realm.createObject(MessageThread::class.java, user2)
            ?: throw Exception("Failed to create thread object.")
    }

    public fun getOrCreateThreadObject(realm: Realm, user2: String): MessageThread {
        var thread = getThreadObject(realm, user2)
        if (thread == null) thread = createThreadObject(realm, user2)
        return thread
    }

    public fun createContactObject(realm: Realm, number: String): Contact {
        return realm.createObject(Contact::class.java, number)
            ?: throw Exception("Failed to create contact object.")
    }

    public fun getOrCreateContactObject(realm: Realm, number: String): Contact {
        var contact = getContactObject(realm, number)
        if (contact == null) contact = createContactObject(realm, number)
        return contact
    }

    public fun createRuleObject(realm: Realm, user2: String): Rule {
        return realm.createObject(Rule::class.java, user2)
            ?: throw Exception("Failed to create rule object.")
    }

    public fun getOrCreateRuleObject(realm: Realm, user2: String): Rule? {
        var rule = getRuleObject(realm, user2)
        if (rule == null) rule = createRuleObject(realm, user2)
        return rule
    }

    public fun createGlobalParamObject(realm: Realm, attrName: String): GlobalParam {
        return realm.createObject(GlobalParam::class.java, attrName)
            ?: throw Exception("Failed to create global param object.")
    }

    public fun getOrCreateGlobalParamObject(realm: Realm, attrName: String): GlobalParam {
        var globalParam = getGlobalParamObject(realm, attrName)
        if (globalParam == null) globalParam = createGlobalParamObject(realm, attrName)
        return globalParam
    }

    /* Get by primary key, can be called outside of transaction */

    public fun getMessageObject(realm: Realm, messageId: String): Message? {
        return realm.where(Message::class.java).equalTo("id", messageId).findFirst()
    }

    public fun getThreadObject(realm: Realm, user2: String): MessageThread? {
        return realm.where(MessageThread::class.java).equalTo("user2", user2).findFirst()
    }

    public fun getContactObject(realm: Realm, number: String): Contact? {
        return realm.where(Contact::class.java).equalTo("number", number).findFirst()
    }

    public fun getRuleObject(realm: Realm, user2: String): Rule? {
        return realm.where(Rule::class.java).equalTo("user2", user2).findFirst()
    }

    public fun getGlobalParamObject(realm: Realm, attrName: String): GlobalParam? {
        return realm.where(GlobalParam::class.java).equalTo("attrName", attrName).findFirst()
    }
}