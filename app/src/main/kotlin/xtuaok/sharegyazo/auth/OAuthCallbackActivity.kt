package xtuaok.sharegyazo.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import xtuaok.sharegyazo.GyazoPreference
import xtuaok.sharegyazo.R

/** Receives `sharegyazo://oauth/<provider>?...` from the login relay and finishes the login. */
class OAuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (savedInstanceState != null || AccountLogin.providerOf(uri) == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val result = AccountLogin.complete(applicationContext, uri!!)
            val name = result.provider?.displayName.orEmpty()
            when (result) {
                is AccountLogin.Result.Success -> toast(getString(R.string.login_succeeded, name))
                is AccountLogin.Result.Failure -> toast(getString(R.string.login_failed, name, result.message))
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
