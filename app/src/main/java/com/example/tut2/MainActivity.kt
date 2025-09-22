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

class MainActivity : AppCompatActivity() {
    private lateinit var etIn: EditText
    private lateinit var btnSend: Button
    private lateinit var textOut: TextView

    private val conversation = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etIn = findViewById(R.id.etIn)
        btnSend = findViewById(R.id.btnSend)
        textOut = findViewById(R.id.textOut)

        textOut.movementMethod = ScrollingMovementMethod()

        if (savedInstanceState != null) {
            conversation.append(savedInstanceState.getString("conversation", ""))
            textOut.text = conversation.toString()
            etIn.setText(savedInstanceState.getString("input", ""))
        }

        btnSend.setOnClickListener {
            val userMsg = etIn.text.toString().trim()

            if (userMsg.isNotEmpty()) {
                appendMessage("Du", userMsg)

                etIn.text.clear()

                val botReply = "Ich bin computer-generiert."
                appendMessage("App", botReply)

            } else {
                etIn.error = "Bitte eine Nachricht eingeben"
            }
        }
    }

    private fun appendMessage(sender: String, message: String) {
        if (conversation.isNotEmpty()) conversation.append("\n\n")
        conversation.append("$sender: $message")
        textOut.text = conversation.toString()

        textOut.post {
            val scrollAmount = textOut.layout?.getLineTop(textOut.lineCount) ?: 0
            if (scrollAmount > textOut.height) {
                textOut.scrollTo(0, scrollAmount - textOut.height)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("conversation", conversation.toString())
        outState.putString("input", etIn.text.toString())
    }
}