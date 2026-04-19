package com.example.ulamshare

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ContactSupportActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private lateinit var dbRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_support)

        recycler = findViewById(R.id.recyclerChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        // Setup RecyclerView
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true   // newest message always at bottom
        adapter = ChatAdapter(messages)
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        dbRef = FirebaseDatabase.getInstance()
            .getReference("supportChats")
            .child(user.uid)

        // Save user info once
        dbRef.updateChildren(
            mapOf(
                "email" to user.email,
                "userId" to user.uid
            )
        )

        btnSend.setOnClickListener {
            sendMessage()
        }

        listenForMessages()
    }

    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()

        if (messageText.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val messageRef = dbRef.child("messages").push()

        val message = mapOf(
            "text" to messageText,
            "sender" to "user",
            "time" to System.currentTimeMillis()
        )

        messageRef.setValue(message)
            .addOnSuccessListener {
                etMessage.setText("")   // clear input only on success
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenForMessages() {
        dbRef.child("messages")
            .orderByChild("time")   // ensures correct chronological order
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    messages.clear()

                    for (data in snapshot.children) {
                        val text   = data.child("text").getValue(String::class.java) ?: ""
                        val sender = data.child("sender").getValue(String::class.java) ?: ""
                        val time   = data.child("time").getValue(Long::class.java) ?: 0L

                        messages.add(ChatMessage(text, sender, time))
                    }

                    adapter.notifyDataSetChanged()

                    if (messages.isNotEmpty()) {
                        recycler.scrollToPosition(messages.size - 1)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@ContactSupportActivity,
                        "Error loading messages: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }
}