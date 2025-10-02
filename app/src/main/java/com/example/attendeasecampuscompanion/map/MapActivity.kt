package com.example.attendeasecampuscompanion.map
// TEAM: Lightweight Activity whose ONLY job is to host the map fragment.
// This keeps MainActivity focused on sign-in/home.

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.attendeasecampuscompanion.R
import com.example.attendeasecampuscompanion.map.CampusMapFragment

class MapActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MapActivity", "onCreate()")
        android.widget.Toast.makeText(this, "Opening Map…", android.widget.Toast.LENGTH_SHORT).show()
        setContentView(R.layout.activity_map)

        // Optional: show a back arrow in the top bar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.app_name) + " • Map"
        }

        // Insert the map fragment into this Activity's container (once)
        if (supportFragmentManager.findFragmentById(R.id.map_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.map_container, CampusMapFragment())
                .commit()
        }
    }

    // Make the action-bar back arrow work
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
