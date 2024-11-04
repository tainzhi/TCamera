package com.tainzhi.android.tcamera.ui

import android.Manifest
import android.R
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.tainzhi.android.tcamera.REQUEST_CAMERA_PERMISSION

/**
 * @author:       tainzhi
 * @mail:         qfq61@qq.com
 * @date:         2019/11/27 上午11:34
 * @description:
 **/

class ConfirmationDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(activity)
                    .setMessage("This app needs camera permission")
                    .setPositiveButton(R.string.ok) { _, _ ->
                        parentFragment?.requestPermissions(arrayOf(Manifest.permission.CAMERA),
                            REQUEST_CAMERA_PERMISSION
                        )
                    }
                    .setNegativeButton(R.string.cancel) { _, _ ->
                        parentFragment?.activity?.finish()
                    }
                    .create()
}
