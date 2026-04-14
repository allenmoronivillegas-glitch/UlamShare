package com.example.ulamshare

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import android.widget.Toast

class ContactSupportActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_support)

        // 🔗 Connect UI
        recycler = findViewById(R.id.recyclerChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        // 📜 Setup RecyclerView
        adapter = ChatAdapter(messages)
        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(this)

        // 📤 Send button
        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()

            if (text.isNotEmpty()) {
                sendMessage() // ✅ REMOVE "text"
            }
        }

        // 👂 Listen for messages
        listenForMessages()
    }

    // 📤 SEND MESSAGE TO FIREBASE
    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()
        val user = FirebaseAuth.getInstance().currentUser

        if (messageText.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = user.uid

        val dbRef = FirebaseDatabase.getInstance()
            .getReference("supportChats")
            .child(userId)

        // Save user info
        val userInfo = mapOf(
            "email" to user.email,
            "userId" to userId
        )

        dbRef.updateChildren(userInfo)

        // Send message
        val messageRef = dbRef.child("messages").push()

        val message = mapOf(
            "text" to messageText,
            "sender" to "user",
            "time" to System.currentTimeMillis()
        )

        messageRef.setValue(message)
            .addOnSuccessListener {
            }
    }

    // 👂 LISTEN FOR MESSAGES
    private fun listenForMessages() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid

        val dbRef = FirebaseDatabase.getInstance()
            .getReference("supportChats")
            .child(userId)
            .child("messages")

        dbRef.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                messages.clear()

                for (data in snapshot.children) {
                    val msg = data.child("text").value.toString()
                    val sender = data.child("sender").value.toString()

                    messages.add(ChatMessage(msg, sender))
                }

                adapter.notifyDataSetChanged()

                if (messages.isNotEmpty()) {
                    recycler.scrollToPosition(messages.size - 1)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // optional: handle error
            }
        })
    }
}