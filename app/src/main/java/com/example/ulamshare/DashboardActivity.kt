package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val btnSignIn = findViewById<Button>(R.id.btnSignIn)
        val btnDonateNow = findViewById<Button>(R.id.btnDonateNow)
        
        // Quick Donate Buttons from content_dashboard
        val btnDonate100 = findViewById<Button>(R.id.btnDonate100)
        val btnDonate300 = findViewById<Button>(R.id.btnDonate300)
        val btnDonate500 = findViewById<Button>(R.id.btnDonate500)

        btnSignIn.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnDonateNow.setOnClickListener {
            showGuestBottomSheet()
        }

        val donateListener = { _: android.view.View ->
            showGuestBottomSheet()
        }

        btnDonate100.setOnClickListener(donateListener)
        btnDonate300.setOnClickListener(donateListener)
        btnDonate500.setOnClickListener(donateListener)
    }

    private fun showGuestBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.activity_guest, null)
        bottomSheetDialog.setContentView(view)

        val btnCreateAccount = view.findViewById<Button>(R.id.btnCreateAccount)
        val btnLogin = view.findViewById<Button>(R.id.btnLogin)
        val btnContinueGuest = view.findViewById<TextView>(R.id.btnContinueGuest)

        btnCreateAccount.setOnClickListener {
            bottomSheetDialog.dismiss()
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnLogin.setOnClickListener {
            bottomSheetDialog.dismiss()
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnContinueGuest.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }
}