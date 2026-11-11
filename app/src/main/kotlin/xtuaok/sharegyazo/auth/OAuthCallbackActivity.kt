package xtuaok.sharegyazo.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import xtuaok.sharegyazo.GyazoPreference
import xtuaok.sharegyazo.R

/** Receives `sharegyazo://oauth/gyazo?...` from the login relay and finishes the login. */
class OAuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (savedInstanceState != null || !GyazoLogin.isCallback(uri)) {
            finish()
            return
        }
        lifecycleScope.launch {
            when (val result = GyazoLogin.complete(applicationContext, uri!!)) {
                GyazoLogin.Result.Success -> toast(getString(R.string.gyazo_login_succeeded))
                is GyazoLogin.Result.Failure -> toast(getString(R.string.gyazo_login_failed, result.message))
            }
            // Back to the settings screen the login was started from.
            startActivity(
                Intent(this@OAuthCallbackActivity, GyazoPreference::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
            )
            finish()
        }
    }

    private fun toast(text: String) = Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
}
