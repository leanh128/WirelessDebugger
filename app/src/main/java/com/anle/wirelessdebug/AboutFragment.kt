package com.anle.wirelessdebug

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.anle.wirelessdebug.databinding.FragmentAboutBinding
import com.anle.wirelessdebug.databinding.FragmentCommandBinding

class AboutFragment : Fragment() {
    lateinit var binding: FragmentAboutBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvAbout.setOnClickListener {
            val uriBuilder = Uri.parse("https://play.google.com/store/apps/dev")
                .buildUpon()
                .appendQueryParameter("id", "5525099005655730103")
//                .appendQueryParameter("launch", "true")
            Intent(Intent.ACTION_VIEW).apply {
                data = uriBuilder.build()
                setPackage("com.android.vending")
            }.let { startActivity(it) }
        }

        binding.tvAbove11.text =
            "https://developer.android.com/studio/command-line/adb#connect-to-a-device-over-wi-fi-android-11+"
        binding.tvBelow11.text = "https://developer.android.com/studio/command-line/adb#wireless"
        binding.btnAbove11.setOnClickListener {
            val uriBuilder =
                Uri.parse("https://developer.android.com/studio/command-line/adb#connect-to-a-device-over-wi-fi-android-11+")
                    .buildUpon()
            Intent(Intent.ACTION_VIEW).apply {
                data = uriBuilder.build()
            }.let { startActivity(it) }
        }
        binding.btnLinkBelow11.setOnClickListener {
            val uriBuilder =
                Uri.parse("https://developer.android.com/studio/command-line/adb#wireless")
                    .buildUpon()
            Intent(Intent.ACTION_VIEW).apply {
                data = uriBuilder.build()
            }.let { startActivity(it) }
        }
    }

}