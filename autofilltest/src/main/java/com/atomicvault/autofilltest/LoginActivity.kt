package com.atomicvault.autofilltest

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** A login form with the shape real apps use, for the CI Autofill check. */
class LoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
        }
        layout.addView(TextView(this).apply { text = "Test login" })
        layout.addView(EditText(this).apply {
            hint = "Username"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setAutofillHints(View.AUTOFILL_HINT_USERNAME)
            contentDescription = "username_field"
        })
        layout.addView(EditText(this).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            contentDescription = "password_field"
        })
        layout.addView(Button(this).apply { text = "Sign in" })
        setContentView(layout)
    }
}
