package com.example.ulamshare

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
class ActivitySelectAmount : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_amount)

        val campaignId = intent.getStringExtra("campaignId")
        val title = intent.getStringExtra("title")

        val tvTitle = findViewById<TextView>(R.id.tvTitle)

        // 🔥 Dynamic title
        tvTitle.text = "Donate to ${title ?: "Campaign"}"
    }
}
