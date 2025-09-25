package com.example.tut2

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
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
    private lateinit var startScreen: View
    private lateinit var chatScreen: View

    private lateinit var etIn: EditText
    private lateinit var btnSend: ImageButton

    private lateinit var chatRecycler: RecyclerView

    private lateinit var btnBack: ImageButton

    private val conversation = mutableListOf<Message>()
    private lateinit var chatAdapter: ChatAdapter





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


//        chatSend.setOnClickListener {
//            val userMsg = chatInput.text.toString().trim()
//            if (userMsg.isNotEmpty()) {
//                conversation.add(Message("User", userMsg))
//                chatInput.text.clear()
//
//                val botReply = "Antwort auf: $userMsg"
//                conversation.add(Message("Bot", botReply))
//
//                chatAdapter.notifyDataSetChanged()
//                chatRecycler.scrollToPosition(conversation.size - 1)
//            }
//        }

        btnBack.setOnClickListener {
            chatScreen.visibility = View.GONE
            startScreen.visibility = View.VISIBLE
            etIn.text.clear()
        }




    }
    private fun appendMessage(sender: String, message: String) {
        // Neue Nachricht zur Liste hinzufügen
        conversation.add(Message(sender, message))

        // Adapter benachrichtigen, dass sich Daten geändert haben
        chatAdapter.notifyItemInserted(conversation.size - 1)

        // Scrollen zum letzten Eintrag
        chatRecycler.scrollToPosition(conversation.size - 1)
    }



//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        outState.putString("conversation", conversation.toString())
//        outState.putString("input", etIn.text.toString())
//    }
}