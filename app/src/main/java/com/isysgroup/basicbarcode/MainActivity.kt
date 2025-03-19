package com.isysgroup.basicbarcode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.ScanContract
import android.util.Log
import android.widget.ImageView
import android.widget.RatingBar
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class MainActivity : AppCompatActivity() {

    private lateinit var isbnEditText: TextView
    private lateinit var titleEditText: TextView
    private lateinit var AuthorEditText: TextView
    private lateinit var publishedDateEditText: TextView
    private lateinit var descriptionEditText: TextView
    private lateinit var pagesEditText: TextView
    private lateinit var ratingBar: RatingBar
    private lateinit var ratingEditText: TextView
    private lateinit var coverURLEditText: TextView
    private lateinit var coverImageView: ImageView

    private lateinit var barcodeLauncher: ActivityResultLauncher<ScanOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        val apiKey = getString(R.string.authorization_key)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the TextViews
        isbnEditText = findViewById(R.id.ISBN)
        titleEditText = findViewById(R.id.title)
        AuthorEditText = findViewById(R.id.author)
        publishedDateEditText = findViewById(R.id.published_date)
        descriptionEditText = findViewById(R.id.book_description)
        coverURLEditText = findViewById(R.id.cover_url)
        pagesEditText = findViewById(R.id.pages)
        ratingEditText = findViewById(R.id.book_rating_value)

        // Initialize the rating bar
        ratingBar = findViewById(R.id.book_rating)

        // Initialize the cover image view
        coverImageView = findViewById(R.id.book_cover_image)

        // Set up the scan button
        val scanButton: Button = findViewById(R.id.scan_button)

        // Set up the scan history button
        val scanHistoryButton: Button = findViewById(R.id.view_history_button)

        // Set up the scan button
        val saveBookEntryButton: Button = findViewById(R.id.save_entry_button)

        // Set up barcode launcher
        barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                val barcode = result.contents
                // Display scanned barcode result
                isbnEditText.text = "$barcode"
                acquireDetails(barcode)
            } else {
                Toast.makeText(this, "Scan was cancelled", Toast.LENGTH_SHORT).show()
            }
        }

        // Request camera permission if not already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
        }

        // Set up entry listener for the ISBN 13 to auto populate the fields when
        // any data for the code is manually entered instead of scanning
        isbnEditText.setOnEditorActionListener { v, actionId, event ->
            val enteredIsbn = isbnEditText.text.toString().trim()
            if (enteredIsbn.isNotEmpty()) {
                acquireDetails(enteredIsbn)
            }
            false
        }
        // Set up click listener for scanning
        scanButton.setOnClickListener {
            startScanner()
        }

        scanHistoryButton.setOnClickListener {
            val historyIntent = Intent(this, ScanHistoryActivity::class.java)
            startActivity(historyIntent)
        }

        // Set up click listener for viewing book scans history
        saveBookEntryButton.setOnClickListener {
            saveEntry()
        }
    }

    private fun clearFields() {
        isbnEditText.text = ""
        titleEditText.text = ""
        AuthorEditText.text = ""
        publishedDateEditText.text = ""
        descriptionEditText.text = ""
        pagesEditText.text = ""
        coverURLEditText.text = ""
        ratingBar.rating = 0f
        ratingEditText.text = ""
        coverImageView.setImageResource(R.drawable.default_cover_image) // Reset to default image
    }

    private fun requestBodyToString(requestBody: RequestBody): String {
        val buffer = Buffer()
        requestBody.writeTo(buffer)
        return buffer.readUtf8()
    }
    private fun saveEntry() {
        // Create a book entry object with the current field values
        val bookEntry = mapOf(
            "isbn" to isbnEditText.text.toString(),
            "title" to titleEditText.text.toString(),
            "author" to AuthorEditText.text.toString(),
            "publishedDate" to publishedDateEditText.text.toString(),
            "description" to descriptionEditText.text.toString(),
            "pages" to pagesEditText.text.toString(),
            "rating" to ratingBar.rating.toString(),
            "coverUrl" to coverURLEditText.text.toString()
        )

        // TODO: Save this entry to a local database for later syncing

        // Show success message
        Toast.makeText(this, "Entry saved successfully!", Toast.LENGTH_SHORT).show()

        // Clear all fields for the next scan
        clearFields()
    }
    // This method is responsible for making the API call using the scanned ISBN
    private fun acquireDetails(isbn: String) {
        val apiKey = getString(R.string.authorization_key)
        val query = "query GetSpecificEdition { editions(where: {isbn_13: {_eq: \"$isbn\"}}) { book { id title release_date slug contributions { id author { name } } pages rating } image { url } description isbn_13 } }"


        val client = OkHttpClient()
        val requestBody = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            JSONObject().put("query", query).toString()
        )
        // Log the request body as a string
        Log.d("GraphQLRequest", "Request Body: ${requestBodyToString(requestBody)}")

        val request = Request.Builder()
            .url("https://api.hardcover.app/v1/graphql")
            .addHeader("authorization", apiKey)
            .post(requestBody)
//            .addHeader("content-type", "application/json")
            .build()

        Thread {
            try {
                val response: Response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d("BookDetails", "HTTP Status Code: ${response.code}")
                Log.d("BookDetails", "Response Body: $responseBody")

                if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                    val json = JSONObject(responseBody)
                    val editions = json.getJSONObject("data").optJSONArray("editions")

                    if (editions != null && editions.length() > 0) {
                        val edition = editions.getJSONObject(0)
                        val book = edition.optJSONObject("book")

                        val title = book?.optString("title", "Title not found") ?: "Title not found"
                        val releaseDate = book?.optString("release_date", "Date not found") ?: "Date not found"
                        val description = edition.optString("description", "Description not found")
                        val isbn13 = edition.optString("isbn_13", "ISBN not found")
                        val pages = book?.optInt("pages", 0)?.toString() ?: "Pages not found"
                        val rating = book?.optDouble("rating", 0.0)?.toString() ?: "Rating not found"

                        val authorArray = book?.optJSONArray("contributions")
                        val author = if (authorArray != null && authorArray.length() > 0) {
                            authorArray.getJSONObject(0).optJSONObject("author")?.optString("name", "Author not found") ?: "Author not found"
                        } else "Author not found"

                        val image = edition.optJSONObject("image")
                        val coverUrl = image?.optString("url", "Cover not found") ?: "Cover not found"

                        runOnUiThread {
                            // Update the text entry fields from the scan result
                            // (The user can also just manually input these if needed)
                            titleEditText.text = "$title"
                            AuthorEditText.text = "$author"
                            publishedDateEditText.text = "$releaseDate"
                            descriptionEditText.text = "$description"
                            isbnEditText.text = "$isbn13"
                            pagesEditText.text = "$pages"
                            coverURLEditText.text = "$coverUrl"

                            // Convert rating from String to Float and update RatingBar
                            val ratingValue = rating.toFloatOrNull() ?: 0.0f
                            ratingBar.rating = ratingValue

                            // Update the text view for the rating value alongside the stars
                            ratingEditText.text = String.format("%.1f", ratingValue)

                            // Load the cover image using Glide
                            if (coverUrl != "Cover not found") {
                                Glide.with(this)
                                    .load(coverUrl)
                                    .into(coverImageView)
                            } else {
                                // Handle case when there is no cover image URL
                                coverImageView.setImageResource(R.drawable.default_cover_image) // Or any default image
                            }
                        }
                    } else {
                        Log.e("BookDetails", "No editions found for ISBN: $isbn")
                        runOnUiThread {
                            Toast.makeText(this, "No book details found.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("BookDetails", "Failed to retrieve book details. HTTP Code: ${response.code}")
                    runOnUiThread {
                        Toast.makeText(this, "Failed to retrieve book details.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("BookDetails", "Error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "Error fetching book details.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }


    private fun startScanner() {
        // Set up scan options
        val options = ScanOptions()
        options.setPrompt("Scan a barcode")
        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        options.setOrientationLocked(false)

        // Launch barcode scanner
        barcodeLauncher.launch(options)
    }

    private fun requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            // Show explanation if needed (optional)
            Toast.makeText(this, "Camera permission is required to scan barcodes.", Toast.LENGTH_LONG).show()
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
    }

    // Handle permission request result
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, now you can scan
                    Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }
}
