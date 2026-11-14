package io.github.carrombot

import android.app.Activity
import android.os.Build
import android.os.Bundle
import io.github.carrombot.databinding.ActivitySettingsBinding

/** Honest settings placeholder with normal Android back navigation and no saved permission grants. */
public class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) binding.screenTitle.isAccessibilityHeading = true
        binding.backButton.setOnClickListener { finish() }
    }
}
