package com.example.finalpm_jpsr

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.Period

class PlayerDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_detail)

        val photo = findViewById<ImageView>(R.id.ivPlayerPhoto)
        val name = findViewById<TextView>(R.id.tvPlayerName)
        val age = findViewById<TextView>(R.id.tvPlayerAge)
        val status = findViewById<TextView>(R.id.tvPlayerStatus)
        val playerId = intent.getStringExtra(EXTRA_PLAYER_ID)
        //val playerNationality = intent.getStringExtra(EXTRA_PLAYER_NATIONALITY)

        if (playerId.isNullOrBlank()) {
            status.text = "No se encontró el identificador del jugador."
            return
        }

        lifecycleScope.launch {
            try {
                val player = SportsApi.getPlayer(playerId)
                    .optJSONArray("players")
                    ?.optJSONObject(0)
                    ?: throw IllegalStateException("No se encontró al jugador.")

                name.text = player.optString("strPlayer").ifBlank { "Jugador" }
                age.text = playerAge(player)
                status.visibility = View.GONE

                val imageUrl = player.optString("strThumb")
                if (imageUrl.isNotBlank()) {
                    try {
                        photo.setImageBitmap(SportsApi.downloadImage(imageUrl))
                        photo.visibility = View.VISIBLE
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        status.text = "No se pudo cargar la foto."
                        status.visibility = View.VISIBLE
                    }
                } else {
                    status.text = "Foto no disponible."
                    status.visibility = View.VISIBLE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.text = "No se pudo cargar la información del jugador."
            }
        }
    }

    private fun playerAge(player: JSONObject): String {
        val birthDate = player.optString("dateBorn")
        if (birthDate.length < 10) return "Edad no disponible"
        val age = try {
            Period.between(LocalDate.parse(birthDate.take(10)), LocalDate.now()).years
        } catch (_: Exception) {
            return "Edad no disponible"
        }
        return "$age años"
    }

    companion object {
        const val EXTRA_PLAYER_ID = "player_id"
    }
}
