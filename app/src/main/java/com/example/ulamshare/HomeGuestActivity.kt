package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeGuestActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_guest)

        val btnSignInTop = findViewById<Button>(R.id.btnSignInTop)
        val btnCreateAccount = findViewById<Button>(R.id.btnCreateAccount)
        val btnDonateNow = findViewById<Button>(R.id.btnDonateNow)
        bottomNavigation = findViewById(R.id.bottomNavigation)

        btnSignInTop.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnDonateNow.setOnClickListener {
            Toast.makeText(this, "Please log in first to donate", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, GuestActivity::class.java))
            finish()
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_campaigns -> {
                    startActivity(Intent(this, CampaignsGuestActivity::class.java))
                    true
                }
                R.id.nav_history, R.id.nav_profile -> {
                    startActivity(Intent(this, GuestActivity::class.java))
                    finish()
                    false
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure Home is selected when returning to this activity
        bottomNavigation.selectedItemId = R.id.nav_home
    }
}