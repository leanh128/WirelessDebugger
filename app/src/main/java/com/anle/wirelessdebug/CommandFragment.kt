package com.anle.wirelessdebug

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.anle.wirelessdebug.databinding.FragmentCommandBinding

class CommandFragment : Fragment() {
    lateinit var binding: FragmentCommandBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCommandBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvDoc.setOnClickListener {
            (activity as? WirelessDebugActivity)?.swipeLeft()
        }
    }

    fun setCommand() {
        val ip = Utils.getIPAddress(true)
        binding.tvConnectionCommand.text =
            SpannableString(getString(R.string.command_adb_connect, ip)).apply {
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    length - ip.length,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
    }

    fun setVisibility(isVisible: Boolean) {
        binding.root.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
    }
}