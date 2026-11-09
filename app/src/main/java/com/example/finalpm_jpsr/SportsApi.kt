package com.example.finalpm_jpsr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SportsApi {
    private const val BASE_URL = "https://www.thesportsdb.com/api/v1/json/123/"

    suspend fun searchTeams(name: String): JSONObject = requestJson(
        "searchteams.php?t=${URLEncoder.encode(name, "UTF-8")}"
    )

    suspend fun getPlayers(teamId: String): JSONObject =
        requestJson("lookup_all_players.php?id=$teamId")

    suspend fun getStandings(leagueId: String): JSONObject =
        requestJson("lookuptable.php?l=$leagueId")

    suspend fun getPlayer(playerId: String): JSONObject =
        requestJson("lookupplayer.php?id=$playerId")

    suspend fun downloadImage(imageUrl: String): Bitmap = withContext(Dispatchers.IO) {
        val connection = URL(imageUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("Respuesta HTTP $status al descargar la foto")
            }
            connection.inputStream.use { input ->
                BitmapFactory.decodeStream(input)
                    ?: throw IOException("La imagen recibida no es válida.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun requestJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL("$BASE_URL$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Respuesta HTTP $status")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
