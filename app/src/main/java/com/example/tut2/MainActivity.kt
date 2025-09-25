package com.example.tut2

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var startScreen: View
    private lateinit var chatScreen: View
    private lateinit var etIn: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var chatRecycler: RecyclerView
    private lateinit var btnBack: ImageButton
    private val conversation = mutableListOf<Message>()
    private lateinit var chatAdapter: ChatAdapter



    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startScreen = findViewById(R.id.startScreen)
        chatScreen = findViewById(R.id.chatScreen)

        etIn = findViewById(R.id.etIn)
        btnSend = findViewById(R.id.btnSend)

        chatRecycler = findViewById(R.id.chatRecycler)

        btnBack = findViewById(R.id.btnBack)

        chatAdapter = ChatAdapter(conversation)
        chatRecycler.adapter = chatAdapter
        chatRecycler.layoutManager = LinearLayoutManager(this)



        if (savedInstanceState != null) {
            val savedConversation = savedInstanceState.getStringArrayList("conversation") ?: arrayListOf()
            savedConversation.forEach {
                conversation.add(Message("User", it)) // immer als User, Bot geht verloren
            }
            chatAdapter.notifyDataSetChanged()
            chatRecycler.scrollToPosition(conversation.size - 1)

            etIn.setText(savedInstanceState.getString("input", ""))
        }



        btnSend.setOnClickListener {
            val userMsg = etIn.text.toString().trim()
            if (userMsg.isNotEmpty()) {

                // Neue Konversation starten
                conversation.clear()
                chatAdapter.notifyDataSetChanged() // Adapter leeren

                // Screen wechseln
                startScreen.visibility = View.GONE
                chatScreen.visibility = View.VISIBLE

                // Erste Nachricht hinzufügen
                conversation.add(Message("User", userMsg))
                val botReply = "Ich bin computer-generiert."
                conversation.add(Message("Bot", botReply))

                chatAdapter.notifyDataSetChanged()
                chatRecycler.scrollToPosition(conversation.size - 1)
            }
        }

        btnBack.setOnClickListener {
            chatScreen.visibility = View.GONE
            startScreen.visibility = View.VISIBLE
            etIn.text.clear()
        }




    }
}