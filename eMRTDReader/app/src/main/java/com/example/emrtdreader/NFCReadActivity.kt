package com.example.emrtdreader

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.emrtdreader.data.NfcPassportReader
import com.example.emrtdreader.domain.AccessKey
import com.example.emrtdreader.error.PassportReadException
import com.example.emrtdreader.utils.MRZParser
import java.util.concurrent.Executors

class NFCReadActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    companion object {
        const val EXTRA_MRZ = "MRZ_DATA"
    }

    private lateinit var instructionTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressTextView: TextView
    private lateinit var canEditText: EditText
    private lateinit var readNfcButton: Button

    private var nfcAdapter: NfcAdapter? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val reader = NfcPassportReader()

    private var mrzRaw: String? = null
    private var mrzKey: AccessKey.Mrz? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_read)

        instructionTextView = findViewById(R.id.instructionTextView)
        statusTextView = findViewById(R.id.statusTextView)
        progressBar = findViewById(R.id.progressBar)
        progressTextView = findViewById(R.id.progressTextView)
        canEditText = findViewById(R.id.canEditText)
        readNfcButton = findViewById(R.id.readNfcButton)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        mrzRaw = intent.getStringExtra(EXTRA_MRZ)
        mrzKey = parseMrz(mrzRaw)

        readNfcButton.setOnClickListener {
            if (mrzKey == null) {
                setError("MRZ invalid. Go back and rescan.")
                return@setOnClickListener
            }
            enableReaderMode()
        }

        updateUiReady()
    }

    override fun onResume() {
        super.onResume()
        // Do not auto-enable to avoid unexpected reads; user presses button.
    }

    override fun onPause() {
        super.onPause()
        disableReaderMode()
    }

    private fun enableReaderMode() {
        val adapter = nfcAdapter ?: run {
            setError("NFC not available on this device")
            return
        }
        if (!adapter.isEnabled) {
            setError("NFC is disabled. Enable NFC and try again.")
            return
        }

        progressBar.isIndeterminate = true
        progressTextView.text = "Waiting for NFC tag..."
        statusTextView.text = ""
        instructionTextView.text = "Hold your passport to the NFC area"

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

        adapter.enableReaderMode(this, this, flags, null)
        readNfcButton.isEnabled = false
    }

    private fun disableReaderMode() {
        nfcAdapter?.disableReaderMode(this)
        readNfcButton.isEnabled = true
    }

    override fun onTagDiscovered(tag: Tag) {
        val mrz = mrzKey ?: return
        val can = canEditText.text?.toString()?.trim().orEmpty()

        runOnUiThread {
            progressTextView.text = "Reading passport..."
            statusTextView.text = ""
        }

        executor.execute {
            try {
                val result = reader.read(tag, mrz, can.ifBlank { null })
                runOnUiThread {
                    disableReaderMode()
                    openResult(result.passportData, result.json, result.authResult.name)
                }
            } catch (e: PassportReadException) {
                runOnUiThread {
                    disableReaderMode()
                    setError(e.message ?: "Read failed")
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    disableReaderMode()
                    setError("Read failed: ${t.javaClass.simpleName}")
                }
            }
        }
    }

    private fun openResult(passportData: com.example.emrtdreader.model.PassportData, json: String?, auth: String) {
        val intent = Intent(this, ResultActivity::class.java)
        intent.putExtra("PASSPORT_DATA", passportData)
        intent.putExtra("PASSPORT_JSON", json)
        intent.putExtra("AUTH_RESULT", auth)
        startActivity(intent)
        finish()
    }

    private fun parseMrz(raw: String?): AccessKey.Mrz? {
        if (raw.isNullOrBlank()) return null
        val mrz = MRZParser.normalizeMRZ(raw)
        val parsed = MRZParser.parseMRZ(mrz) ?: return null
        return AccessKey.Mrz(
            documentNumber = parsed.documentNumber,
            dateOfBirthYYMMDD = parsed.dateOfBirth,
            dateOfExpiryYYMMDD = parsed.expiryDate
        )
    }

    private fun updateUiReady() {
        if (mrzKey == null) {
            instructionTextView.text = "MRZ not available"
            progressTextView.text = ""
            statusTextView.text = "Go back and scan MRZ first."
            readNfcButton.isEnabled = false
            return
        }
        instructionTextView.text = "Optionally enter CAN (6 digits) and press Start"
        progressTextView.text = ""
        statusTextView.text = ""
        readNfcButton.isEnabled = true
    }

    private fun setError(msg: String) {
        instructionTextView.text = "Error"
        progressBar.isIndeterminate = false
        progressTextView.text = ""
        statusTextView.text = msg
    }
}
