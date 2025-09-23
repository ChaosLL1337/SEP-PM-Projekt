package com.example.tut2

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.ImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var etIn: EditText
    private lateinit var btnSend: ImageButton

    private lateinit var textOut: RecyclerView
    private val conversation = mutableListOf<Message>()
    private lateinit var chatAdapter: ChatAdapter




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        textOut = findViewById(R.id.textOut)
        chatAdapter = ChatAdapter(conversation)
        textOut.adapter = chatAdapter
        textOut.layoutManager = LinearLayoutManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etIn = findViewById(R.id.etIn)
        btnSend = findViewById(R.id.btnSend)
        textOut = findViewById(R.id.textOut)



        if (savedInstanceState != null) {
            val savedConversation = savedInstanceState.getStringArrayList("conversation") ?: arrayListOf()
            savedConversation.forEach {
                // Hier musst du die Nachricht mit Sender info wieder hinzufügen, z.B. "User: Hallo"
                conversation.add(Message("User", it)) // Oder Bot, je nach Speicherung
            }
            chatAdapter.notifyDataSetChanged()
            textOut.scrollToPosition(conversation.size - 1)

            etIn.setText(savedInstanceState.getString("input", ""))
        }


        btnSend.setOnClickListener {
            val userMsg = etIn.text.toString().trim()
            if (userMsg.isNotEmpty()) {
                conversation.add(Message("User", userMsg))
                etIn.text.clear()

                val botReply = "Ich bin computer-generiert."
                conversation.add(Message("Bot", botReply))

                chatAdapter.notifyDataSetChanged()
                textOut.scrollToPosition(conversation.size - 1)
            }
        }

    }

    private fun appendMessage(sender: String, message: String) {
        // Neue Nachricht zur Liste hinzufügen
        conversation.add(Message(sender, message))

        // Adapter benachrichtigen, dass sich Daten geändert haben
        chatAdapter.notifyItemInserted(conversation.size - 1)

        // Scrollen zum letzten Eintrag
        textOut.scrollToPosition(conversation.size - 1)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("conversation", conversation.toString())
        outState.putString("input", etIn.text.toString())
    }
}