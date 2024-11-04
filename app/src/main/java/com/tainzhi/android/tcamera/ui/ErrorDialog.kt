package com.tainzhi.android.tcamera.ui

import android.R
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment

/**
 * @author:       tainzhi
 * @mail:         qfq61@qq.com
 * @date:         2019/11/27 上午11:21
 * @description:
 **/

class ErrorDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(activity)
                    .setMessage(arguments?.getString(ARG_MESSAGE))
                    .setPositiveButton(R.string.ok) { _, _ -> activity?.finish() }
                    .create()

    companion object {
        @JvmStatic
        private val ARG_MESSAGE = "message"

        @JvmStatic
        fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
            arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
        }
    }
}