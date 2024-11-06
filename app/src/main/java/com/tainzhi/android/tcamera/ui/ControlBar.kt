package com.tainzhi.android.tcamera.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import androidx.appcompat.widget.AppCompatImageButton
import com.tainzhi.android.tcamera.MainActivity
import com.tainzhi.android.tcamera.R
import com.tainzhi.android.tcamera.SettingsActivity
import com.tainzhi.android.tcamera.databinding.ActivityMainBinding
import com.tainzhi.android.tcamera.util.SettingsManager

class ControlBar(
    val context: Context,
    val binding: ActivityMainBinding,
    private val onRatioUpdate: () -> Unit
) {
    private lateinit var inflatedView: View
    private lateinit var ivRatio1x1: AppCompatImageButton
    private lateinit var ivRatio4x3: AppCompatImageButton
    private lateinit var ivRatio16x9: AppCompatImageButton
    private lateinit var ivRatioFull: AppCompatImageButton
    private val btnRatio = binding.btnRatio.apply {
        updateControlBarRatioIcon()
        setOnClickListener {
            binding.clControlBarLevel1Menu.animate().alpha(0.5f)
                .withEndAction {
                    binding.clControlBarLevel1Menu.visibility = View.INVISIBLE
                    inflatePreviewAspectRatioButtons()
                    updatePreviewAspectRatioBtnState()
                    inflatedView.animate()
                        .alpha(1f)
                        .withEndAction {
                            inflatedView.visibility = View.VISIBLE
                        }
                        .start()
                }
                .start()
        }
    }
    private val btnSettings = binding.btnSettings.apply {
        setOnClickListener {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }
    }

    private val btnHdr = binding.btnHdr.apply {
        if (SettingsManager.getInstance().getBoolean(SettingsManager.KEY_HDR_ENABLE)) {
            setImageResource(R.drawable.ic_hdr_on)
        } else {
            setImageResource(R.drawable.ic_hdr_off)
        }
        setOnClickListener {
            if (SettingsManager.getInstance().getBoolean(SettingsManager.KEY_HDR_ENABLE)) {
                SettingsManager.getInstance().setBoolean(SettingsManager.KEY_HDR_ENABLE, false)
                setImageResource(R.drawable.ic_hdr_off)
            } else {
                SettingsManager.getInstance().setBoolean(SettingsManager.KEY_HDR_ENABLE, true)
                setImageResource(R.drawable.ic_hdr_on)
            }
        }
    }

    fun rotate(angle: Int) {
        for (view in arrayOf(btnSettings, btnRatio, binding.btnHdr)) {
            view.animate()
                .setDuration(800)
                .rotation(angle.toFloat())
                .start()
        }
    }

    fun updateByCameraMode(cameraMode: Int) {
        when (cameraMode) {
            MainActivity.VIDEO_MODE -> {
                btnHdr.visibility = View.INVISIBLE
                btnRatio.visibility = View.INVISIBLE
            }

            MainActivity.IMAGE_MODE -> {
                btnHdr.visibility = View.VISIBLE
                btnRatio.visibility = View.VISIBLE
            }
        }
    }

    /**
     * 显示预览比例选择菜单
     */
    private fun inflatePreviewAspectRatioButtons() {
        if (!this::inflatedView.isInitialized) {
            inflatedView = binding.vsControlBarRatio.inflate()
            ivRatio1x1 = inflatedView.findViewById<AppCompatImageButton>(R.id.btn_ratio_1x1).apply {
                setOnClickListener {
                    postChangePreviewAspectRatio(SettingsManager.PreviewAspectRatio.RATIO_1x1)
                }
            }
            ivRatio4x3 = inflatedView.findViewById<AppCompatImageButton>(R.id.btn_ratio_4x3).apply {
                setOnClickListener {
                    postChangePreviewAspectRatio(SettingsManager.PreviewAspectRatio.RATIO_4x3)
                }
            }
            ivRatio16x9 =
                inflatedView.findViewById<AppCompatImageButton>(R.id.btn_ratio_16x9).apply {
                    setOnClickListener {
                        postChangePreviewAspectRatio(SettingsManager.PreviewAspectRatio.RATIO_16x9)
                    }
                }
            ivRatioFull =
                inflatedView.findViewById<AppCompatImageButton>(R.id.btn_ratio_full).apply {
                    setOnClickListener {
                        postChangePreviewAspectRatio(SettingsManager.PreviewAspectRatio.RATIO_FULL)
                    }
                }
        }
    }

    private fun updatePreviewAspectRatioBtnState() {
        val previewRatio = SettingsManager.getInstance().getPreviewAspectRatio()
        when (previewRatio) {
            SettingsManager.PreviewAspectRatio.RATIO_1x1 -> {
                ivRatio1x1.isSelected = true
            }

            SettingsManager.PreviewAspectRatio.RATIO_4x3 -> {
                ivRatio4x3.isSelected = true
            }

            SettingsManager.PreviewAspectRatio.RATIO_16x9 -> {
                ivRatio16x9.isSelected = true
            }

            SettingsManager.PreviewAspectRatio.RATIO_FULL -> {
                ivRatioFull.isSelected = true
            }
        }
    }

    private fun postChangePreviewAspectRatio(previewAspectRatio: SettingsManager.PreviewAspectRatio) {
        Log.d(
            "qfq",
            "postChangePreviewAspectRatio: ${SettingsManager.getInstance().getPreviewAspectRatio()}"
        )
        if (previewAspectRatio != SettingsManager.getInstance().getPreviewAspectRatio()) {
            SettingsManager.getInstance().setPreviewRatio(previewAspectRatio)
            onRatioUpdate.invoke()
            updateControlBarRatioIcon()
            inflatedView.animate().alpha(0.5f)
                .withEndAction {
                    inflatedView.visibility = View.INVISIBLE
                    // 所有button先全部取消选中
                    ivRatio1x1.isSelected = false
                    ivRatio4x3.isSelected = false
                    ivRatio16x9.isSelected = false
                    ivRatioFull.isSelected = false
                    // 再选中特定button
                    updatePreviewAspectRatioBtnState()
                    binding.clControlBarLevel1Menu.animate()
                        .alpha(1f)
                        .withEndAction {
                            binding.clControlBarLevel1Menu.visibility = View.VISIBLE
                        }
                        .start()
                }
                .start()
        }
    }

    private fun updateControlBarRatioIcon() {
        val previewRatio = SettingsManager.getInstance().getPreviewAspectRatio()
        binding.btnRatio.setImageDrawable(
            context.resources.getDrawable(
                when (previewRatio) {
                    SettingsManager.PreviewAspectRatio.RATIO_1x1 -> R.drawable.ic_ratio_1x1
                    SettingsManager.PreviewAspectRatio.RATIO_4x3 -> R.drawable.ic_ratio_4x3
                    SettingsManager.PreviewAspectRatio.RATIO_16x9 -> R.drawable.ic_ratio_16x9
                    else -> R.drawable.ic_ratio_full
                }
            )
        )
    }

    companion object {
        private val TAG = ControlBar::class.java.simpleName
    }
}
