package edu.nyitLI.attendease

// TEAM NOTE: Single-activity approach.
// This activity just hosts CampusMapFragment in activity_main.xml's container.

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import edu.nyitLI.attendease.ui.CampusMapFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Only add the fragment on first creation (not on rotation/restore)
        if (supportFragmentManager.findFragmentById(R.id.container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, CampusMapFragment())
                .commit()
        }
    }
}
