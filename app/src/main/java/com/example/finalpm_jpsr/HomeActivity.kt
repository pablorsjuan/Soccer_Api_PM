package com.example.finalpm_jpsr

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

class HomeActivity : AppCompatActivity() {
    private lateinit var searchButton: Button
    private lateinit var searchField: EditText
    private lateinit var teamLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var playersAdapter: PlayerAdapter
    private lateinit var standingsAdapter: TextListAdapter
    private var searchRequestId = 0
    private var selectedTeamId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        searchButton = findViewById(R.id.button)
        searchField = findViewById(R.id.etBuscar)
        teamLabel = findViewById(R.id.tvTeam)
        statusLabel = findViewById(R.id.tvStatus)
        playersAdapter = PlayerAdapter { player ->
            startActivity(
                Intent(this, PlayerDetailActivity::class.java)
                    .putExtra(PlayerDetailActivity.EXTRA_PLAYER_ID, player.id)
            )
        }
        standingsAdapter = TextListAdapter()

        findViewById<RecyclerView>(R.id.recyclerPlayers).apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = playersAdapter
            isNestedScrollingEnabled = false
        }
        findViewById<RecyclerView>(R.id.recyclerRanking).apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = standingsAdapter
            isNestedScrollingEnabled = false
        }

        searchButton.setOnClickListener {
            val teamName = searchField.text.toString().trim()
            if (teamName.isBlank()) {
                Toast.makeText(this, "Escribe el nombre de un equipo", Toast.LENGTH_SHORT).show()
            } else {
                searchTeams(teamName)
            }
        }
    }

    private fun searchTeams(name: String) {
        val requestId = ++searchRequestId
        searchButton.isEnabled = false
        statusLabel.text = "Buscando equipos..."
        selectedTeamId = null
        teamLabel.text = ""
        playersAdapter.submit(emptyList(), "Busca un equipo para ver sus jugadores.")
        standingsAdapter.submit(emptyList())

        lifecycleScope.launch {
            try {
                val response = SportsApi.searchTeams(name)
                if (requestId != searchRequestId) return@launch
                val teamsJson = response.optJSONArray("teams")
                if (teamsJson == null || teamsJson.length() == 0) {
                    statusLabel.text = "No se encontraron equipos con ese nombre."
                    teamLabel.text = ""
                    playersAdapter.submit(emptyList(), "Busca otro equipo para ver sus jugadores.")
                    standingsAdapter.submit(emptyList())
                    selectedTeamId = null
                    return@launch
                }

                val teams = (0 until teamsJson.length()).mapNotNull { teamsJson.optJSONObject(it) }
                if (teams.size == 1) {
                    showTeam(teams.first())
                } else {
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("Selecciona un equipo")
                        .setItems(teams.map { it.optString("strTeam", "Equipo") }.toTypedArray()) { _, which ->
                            teams.getOrNull(which)?.let(::showTeam)
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                    statusLabel.text = "Se encontraron ${teams.size} equipos."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestId == searchRequestId) {
                    statusLabel.text = "No se pudo buscar el equipo."
                    Toast.makeText(this@HomeActivity, "Error al buscar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                if (requestId == searchRequestId) searchButton.isEnabled = true
            }
        }
    }

    private fun showTeam(team: JSONObject) {
        val teamId = team.optString("idTeam")
        if (teamId.isBlank()) {
            Toast.makeText(this, "La API devolvió un equipo sin identificador.", Toast.LENGTH_LONG).show()
            return
        }

        selectedTeamId = teamId
        teamLabel.text = team.optString("strTeam", "Equipo")
        statusLabel.text = "Cargando jugadores..."
        playersAdapter.submit(emptyList(), "Cargando jugadores...")
        standingsAdapter.submit(listOf("Cargando tabla..."))
        loadPlayers(teamId)
        loadFirstLeague(team, teamId)
    }

    private fun loadPlayers(teamId: String) {
        lifecycleScope.launch {
            try {
                val response = SportsApi.getPlayers(teamId)
                if (selectedTeamId != teamId) return@launch
                val playersJson = response.optJSONArray("player")
                val players = if (playersJson == null) {
                    emptyList()
                } else {
                    (0 until playersJson.length()).mapNotNull { index ->
                        playersJson.optJSONObject(index)?.let { player ->
                            Player(
                                id = player.optString("idPlayer"),
                                name = player.optString("strPlayer", "Jugador"),
                                position = player.optString("strPosition"),
                                nationality = player.optString("strNationality")
                            )
                        }
                    }
                }
                if (players.isEmpty()) {
                    playersAdapter.submit(emptyList(), "No hay jugadores disponibles para este equipo.")
                    statusLabel.text = "No se encontraron jugadores."
                } else {
                    playersAdapter.submit(players, "")
                    statusLabel.text = "Pulsa «Ver más» para consultar los datos del jugador."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (selectedTeamId == teamId) {
                    playersAdapter.submit(emptyList(), "No se pudieron cargar los jugadores.")
                    statusLabel.text = "Error al cargar los jugadores."
                    Toast.makeText(this@HomeActivity, "Error de jugadores: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadFirstLeague(team: JSONObject, teamId: String) {
        val leagueIds = team.keys().asSequence()
            .filter { it.startsWith("idLeague") }
            .sortedBy { key -> key.removePrefix("idLeague").toIntOrNull() ?: 0 }

        val leagueKey = leagueIds.firstOrNull { key ->
            team.optString(key).isNotBlank() && team.optString(key) != "0"
        }
        if (leagueKey == null) {
            standingsAdapter.submit(listOf("Este equipo no tiene ligas disponibles."))
            return
        }

        val leagueId = team.optString(leagueKey)
        val suffix = leagueKey.removePrefix("idLeague")
        val leagueName = team.optString("strLeague$suffix")
            .takeIf(String::isNotBlank) ?: "Liga $leagueId"
        loadStandings(League(leagueId, leagueName), teamId)
    }

    private fun loadStandings(league: League, teamId: String) {
        lifecycleScope.launch {
            try {
                val response = SportsApi.getStandings(league.id)
                if (selectedTeamId != teamId) return@launch
                val table = response.optJSONArray("table")
                if (table == null || table.length() == 0) {
                    standingsAdapter.submit(listOf("No hay tabla disponible para ${league.name}."))
                    return@launch
                }

                val rows = (0 until table.length()).mapNotNull { index ->
                    table.optJSONObject(index)?.let { entry ->
                        val rank = entry.optString("intRank", (index + 1).toString())
                        val name = entry.optString("strTeam", "Equipo")
                        val played = entry.optString("intPlayed", "-")
                        val points = entry.optString("intPoints", "-")
                        val wins = entry.optString("intWin", "-")
                        val draws = entry.optString("intDraw", "-")
                        val losses = entry.optString("intLoss", "-")
                        "$rank. $name\nPJ $played   PG $wins   PE $draws   PP $losses   Pts $points"
                    }
                }
                standingsAdapter.submit(rows.ifEmpty { listOf("La tabla no contiene posiciones.") })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (selectedTeamId == teamId) {
                    standingsAdapter.submit(listOf("No se pudo cargar la tabla de ${league.name}."))
                    Toast.makeText(this@HomeActivity, "Error de tabla: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private data class League(val id: String, val name: String)

    private data class Player(
        val id: String,
        val name: String,
        val position: String,
        val nationality: String
    )

    private class PlayerAdapter(
        private val onMoreClick: (Player) -> Unit
    ) : RecyclerView.Adapter<PlayerAdapter.RowViewHolder>() {
        private var items: List<Player> = emptyList()
        private var emptyMessage: String = ""

        fun submit(players: List<Player>, message: String) {
            items = players
            emptyMessage = message
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
            val row = LinearLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(12, 8, 8, 8)
            }
            val details = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 15f
                setTextColor(0xFF172B4D.toInt())
            }
            val button = Button(parent.context).apply {
                text = "Ver más"
                isAllCaps = false
            }
            row.addView(details)
            row.addView(button)
            return RowViewHolder(row, details, button)
        }

        override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
            val player = items.getOrNull(position)
            if (player == null) {
                holder.details.text = emptyMessage
                holder.button.visibility = View.GONE
            } else {
                holder.details.text = listOf(player.name, player.position, player.nationality)
                    .filter(String::isNotBlank)
                    .joinToString(" · ")
                holder.button.visibility = View.VISIBLE
                holder.button.setOnClickListener { onMoreClick(player) }
            }
        }

        override fun getItemCount(): Int = if (items.isEmpty()) 1 else items.size

        class RowViewHolder(
            itemView: View,
            val details: TextView,
            val button: Button
        ) : RecyclerView.ViewHolder(itemView)
    }

    private class TextListAdapter : RecyclerView.Adapter<TextListAdapter.RowViewHolder>() {
        private var rows: List<String> = emptyList()

        fun submit(items: List<String>) {
            rows = items
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
            val text = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(12, 14, 12, 14)
                textSize = 15f
                setTextColor(0xFF172B4D.toInt())
            }
            return RowViewHolder(text)
        }

        override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
            holder.text.text = rows[position]
        }

        override fun getItemCount(): Int = rows.size

        class RowViewHolder(val text: TextView) : RecyclerView.ViewHolder(text)
    }
}
