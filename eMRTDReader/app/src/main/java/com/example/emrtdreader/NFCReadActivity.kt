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
import androidx.lifecycle.lifecycleScope
import com.example.emrtdreader.data.NfcPassportReader
import com.example.emrtdreader.domain.AccessKey
import com.example.emrtdreader.error.PassportReadException
import com.example.emrtdreader.utils.MRZParser
import kotlinx.coroutines.launch

class NFCReadActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    companion object {
        const val EXTRA_MRZ_STRING = "EXTRA_MRZ_STRING"
        const val EXTRA_DOC_NUM = "EXTRA_DOC_NUM"
        const val EXTRA_DOB = "EXTRA_DOB" // YYMMDD
        const val EXTRA_DOE = "EXTRA_DOE" // YYMMDD
    }

    private lateinit var instructionTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressTextView: TextView
    private lateinit var canEditText: EditText
    private lateinit var readNfcButton: Button

    private var nfcAdapter: NfcAdapter? = null
    private val reader = NfcPassportReader()

    private var accessKey: AccessKey.Mrz? = null

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

        val mrzString = intent.getStringExtra(EXTRA_MRZ_STRING)
        if (mrzString != null) {
            accessKey = parseMrz(mrzString)
        } else {
            val docNum = intent.getStringExtra(EXTRA_DOC_NUM)
            val dob = intent.getStringExtra(EXTRA_DOB)
            val doe = intent.getStringExtra(EXTRA_DOE)
            if (docNum != null && dob != null && doe != null) {
                accessKey = AccessKey.Mrz(docNum, dob, doe)
            }
        }

        readNfcButton.setOnClickListener {
            if (accessKey == null) {
                setError("Access key is invalid. Go back and try again.")
                return@setOnClickListener
            }
            enableReaderMode()
        }

        updateUiReady()
    }

    override fun onResume() {
        super.onResume()
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

        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        adapter.enableReaderMode(this, this, flags, null)
        readNfcButton.isEnabled = false
    }

    private fun disableReaderMode() {
        nfcAdapter?.disableReaderMode(this)
        readNfcButton.isEnabled = true
    }

    override fun onTagDiscovered(tag: Tag) {
        val key = accessKey ?: return

        runOnUiThread {
            progressTextView.text = "Reading passport..."
            statusTextView.text = ""
        }

        lifecycleScope.launch {
            try {
                val result = reader.read(tag, key)
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

    private fun openResult(passportData: com.example.emrtdreader.domain.PassportData, json: String?, auth: String) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("PASSPORT_DATA", passportData)
            putExtra("PASSPORT_JSON", json)
            putExtra("AUTH_RESULT", auth)
        }
        startActivity(intent)
        finish()
    }

    private fun parseMrz(raw: String?): AccessKey.Mrz? {
        if (raw.isNullOrBlank()) return null
        // This needs a full MRZ parser, which is not available in MrzNormalizer
        // This part needs to be re-implemented
        return null
    }

    private fun updateUiReady() {
        if (accessKey == null) {
            instructionTextView.text = "Access key not available"
            progressTextView.text = ""
            statusTextView.text = "Go back and scan or enter data first."
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