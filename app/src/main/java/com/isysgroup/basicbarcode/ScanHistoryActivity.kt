package com.isysgroup.basicbarcode

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ScanHistoryActivity : AppCompatActivity() {

    private lateinit var uploadButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_history)

        // Initialize uploadButton after setting the content view
        uploadButton = findViewById(R.id.upload_all_scans)

        // Upload button click (Placeholder logic)
        uploadButton.setOnClickListener {
            Toast.makeText(this, "Uploading scans to database...", Toast.LENGTH_SHORT).show()
            // TODO: Implement database upload logic
        }
    }
}
