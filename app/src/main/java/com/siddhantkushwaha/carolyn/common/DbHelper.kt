package com.siddhantkushwaha.carolyn.common

import android.content.Context
import com.siddhantkushwaha.carolyn.entity.GlobalParam

object DbHelper {

    private const val unsavedNumberClassificationRuleAttrName = "UNKNOWN_NUMBER_CLASSIFICATION"

    public fun setUnsavedNumberClassificationRule(context: Context, isChecked: Boolean) {
        val attrVal = if (isChecked) "1" else "0"
        GlobalParam.setGlobalParam(context, unsavedNumberClassificationRuleAttrName, attrVal)
    }

    public fun getUnsavedNumberClassificationRule(context: Context): Boolean {
        val globalParam =
            GlobalParam.getGlobalParam(context, unsavedNumberClassificationRuleAttrName)
        return globalParam ?: "1" == "1"
    }

}