package com.stepmod.noroot.core

import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri

class ConfigProvider : ContentProvider() {
    companion object {
        const val KEY_MASTER = "master_switch"
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(uri: Uri, proj: Array<String>?, sel: String?, selArgs: Array<String>?, sort: String?): android.database.Cursor? {
        val sp = context?.getSharedPreferences("stepmod_prefs", android.content.Context.MODE_PRIVATE)
        val cursor = MatrixCursor(arrayOf("key", "value"))
        sp?.all?.forEach { (k, v) ->
            cursor.addRow(arrayOf(k, v.toString()))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, sel: String?, selArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, sel: String?, selArgs: Array<String>?): Int = 0
}
