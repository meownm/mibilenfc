package com.example.emrtdreader

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.emrtdreader.crypto.CryptoHelper
import com.example.emrtdreader.utils.MRZParser
import org.jmrtd.BACKeySpec
import org.jmrtd.FeatureStatus
import org.jmrtd.VerificationStatus
import org.jmrtd.cert.CVCPrincipal
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.icao.SODFile
import org.jmrtd.PassportService
import org.jmrtd.cert.CardVerifiableCertificate
import org.jmrtd.PACEKeySpec
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NFCReadActivity : AppCompatActivity() {
    
    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var readNfcButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var executor: ExecutorService
    
    private var mrzData: String = ""
    private var passportData: PassportData? = null
    
    companion object {
        private const val TAG = "NFCReadActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_read)
        
        initViews()
        setupClickListeners()
        
        // Get MRZ data from intent
        mrzData = intent.getStringExtra("MRZ_DATA") ?: ""
        
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        executor = Executors.newSingleThreadExecutor()
    }
    
    private fun initViews() {
        readNfcButton = findViewById(R.id.readNfcButton)
        progressBar = findViewById(R.id.progressBar)
        progressTextView = findViewById(R.id.progressTextView)
        statusTextView = findViewById(R.id.statusTextView)
    }
    
    private fun setupClickListeners() {
        readNfcButton.setOnClickListener {
            if (nfcAdapter.isEnabled) {
                startNFCReading()
            } else {
                Toast.makeText(this, getString(R.string.nfc_not_available), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startNFCReading() {
        progressBar.visibility = android.view.View.VISIBLE
        progressTextView.text = getString(R.string.reading_data)
        statusTextView.visibility = android.view.View.GONE
        readNfcButton.isEnabled = false
        
        // Show instructions to place passport near device
        progressTextView.text = getString(R.string.place_passport_nfc)
    }
    
    override fun onResume() {
        super.onResume()
        if (nfcAdapter.isEnabled) {
            val intentFilters = arrayOf(android.content.IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))
            val techLists = arrayOf(arrayOf(IsoDep::class.java.name))
            nfcAdapter.enableForegroundDispatch(this, null, intentFilters, techLists)
        }
    }
    
    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.ACTION_TECH_DISCOVERED)
        if (tag != null) {
            executor.execute {
                readPassportData(tag)
            }
        }
    }
    
    private fun readPassportData(tag: Tag) {
        var isoDep: IsoDep? = null
        
        try {
            isoDep = IsoDep.get(tag)
            isoDep.connect()
            isoDep.timeout = 10000 // 10 seconds timeout
            
            val service = PassportService(isoDep, null, 0, false, false)
            service.open()
            
            // Extract BAC keys from MRZ data
            val bacKey = extractBACKeyFromMRZ(mrzData)
            if (bacKey == null) {
                runOnUiThread {
                    statusTextView.text = "Error: Could not extract BAC keys from MRZ"
                    statusTextView.setTextColor(resources.getColor(R.color.red))
                    statusTextView.visibility = android.view.View.VISIBLE
                    readNfcButton.isEnabled = true
                }
                return
            }
            
            // Set BAC key for authentication
            service.setBACKey(bacKey)
            
            // Read SOD file first (contains certificates and hashes)
            progressTextView.text = getString(R.string.progress_reading, "SOD")
            val sodBytes = service.getInputStream(PassportService.EF_SOD).readAllBytes()
            val sodFile = SODFile(ByteArrayInputStream(sodBytes))
            
            // Read DG1 (personal data)
            progressTextView.text = getString(R.string.progress_reading, "DG1")
            val dg1Bytes = service.getInputStream(PassportService.EF_DG1).readAllBytes()
            val dg1File = DG1File(ByteArrayInputStream(dg1Bytes))
            
            // Read DG2 (face photo)
            progressTextView.text = getString(R.string.progress_reading, "DG2")
            val dg2Bytes = service.getInputStream(PassportService.EF_DG2).readAllBytes()
            val dg2File = DG2File(ByteArrayInputStream(dg2Bytes))
            
            // Perform passive authentication
            val verificationStatus = performPassiveAuthentication(sodFile, dg1File, dg2File)
            
            // Extract data
            val personalData = dg1File.personalData
            val faceImage = if (dg2File.faceCount > 0) dg2File.getFace(0).imageData else null
            
            // Create passport data object
            val passportData = PassportData(
                firstName = personalData.firstName,
                lastName = personalData.lastName,
                documentNumber = personalData.documentNumber,
                nationality = personalData.nationality,
                dateOfBirth = personalData.dateOfBirth.toString(),
                expiryDate = personalData.expiryDate.toString(),
                personalNumber = personalData.personalNumber ?: "",
                mrzData = mrzData,
                faceImage = faceImage,
                signatureValid = verificationStatus == VerificationStatus.VALID
            )
            
            this.passportData = passportData
            
            // Update UI on main thread
            runOnUiThread {
                progressTextView.text = getString(R.string.progress_complete)
                statusTextView.text = if (passportData.signatureValid) {
                    getString(R.string.signature_valid)
                } else {
                    getString(R.string.signature_invalid)
                }
                statusTextView.setTextColor(
                    if (passportData.signatureValid) 
                        resources.getColor(R.color.green) 
                    else 
                        resources.getColor(R.color.red)
                )
                statusTextView.visibility = android.view.View.VISIBLE
                
                // Delay to show success message before navigating
                Handler(Looper.getMainLooper()).postDelayed({
                    startResultActivity()
                }, 1000)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading passport data", e)
            runOnUiThread {
                statusTextView.text = "${getString(R.string.error_reading_passport)}: ${e.message}"
                statusTextView.setTextColor(resources.getColor(R.color.red))
                statusTextView.visibility = android.view.View.VISIBLE
                readNfcButton.isEnabled = true
            }
        } finally {
            try {
                isoDep?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing IsoDep", e)
            }
        }
    }
    
    private fun extractBACKeyFromMRZ(mrz: String): BACKeySpec? {
        try {
            // Parse MRZ to extract document number, date of birth, and expiry date
            val lines = mrz.split("\n").map { it.trim() }
            if (lines.isEmpty()) return null
            
            var docNumber = ""
            var dateOfBirth = ""
            var expiryDate = ""
            
            // TD3 format (passport)
            if (lines[0].startsWith("P")) {
                if (lines.size >= 2) {
                    val line1 = lines[0]
                    val line2 = lines[1]
                    
                    // Extract document number (positions 2-9 in line 1)
                    docNumber = if (line1.length >= 9) line1.substring(2, 9).trim() else ""
                    
                    // Extract date of birth (positions 36-41 in line 2)
                    dateOfBirth = if (line2.length >= 41) line2.substring(36, 42) else ""
                    
                    // Extract expiry date (positions 44-49 in line 2)
                    expiryDate = if (line2.length >= 49) line2.substring(44, 50) else ""
                }
            }
            // TD1 format (ID card)
            else if (lines[0].startsWith("I") || lines[0].startsWith("A") || lines[0].startsWith("C")) {
                if (lines.size >= 3) {
                    val line1 = lines[0]
                    val line2 = lines[1]
                    
                    // Extract document number (positions 5-14 in line 1)
                    docNumber = if (line1.length >= 14) line1.substring(5, 14).trim() else ""
                    
                    // Extract date of birth (positions 5-10 in line 2)
                    dateOfBirth = if (line2.length >= 10) line2.substring(5, 11) else ""
                    
                    // Extract expiry date (positions 15-20 in line 2)
                    expiryDate = if (line2.length >= 20) line2.substring(15, 21) else ""
                }
            }
            
            // Validate extracted data
            if (docNumber.isEmpty() || dateOfBirth.isEmpty() || expiryDate.isEmpty()) {
                return null
            }
            
            return BACKeySpec(docNumber, dateOfBirth, expiryDate)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting BAC key from MRZ", e)
            return null
        }
    }
    
    private fun performPassiveAuthentication(
        sodFile: SODFile,
        dg1File: DG1File,
        dg2File: DG2File
    ): VerificationStatus {
        // Use the CryptoHelper to perform passive authentication
        return CryptoHelper.performPassiveAuthentication(sodFile, dg1File, dg2File)
    }
    
    private fun startResultActivity() {
        val intent = Intent(this, ResultActivity::class.java)
        intent.putExtra("PASSPORT_DATA", passportData)
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}

// Data class to hold passport information
data class PassportData(
    val firstName: String,
    val lastName: String,
    val documentNumber: String,
    val nationality: String,
    val dateOfBirth: String,
    val expiryDate: String,
    val personalNumber: String,
    val mrzData: String,
    val faceImage: ByteArray?,
    val signatureValid: Boolean
)