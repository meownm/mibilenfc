package com.example.emrtdreader

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.emrtdreader.data.NfcPassportReader
import com.example.emrtdreader.data.PassportData
import com.example.emrtdreader.databinding.ActivityNfcReadBinding
import com.example.emrtdreader.models.MrzResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NFCReadActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    companion object {
        const val EXTRA_MRZ_RESULT = "EXTRA_MRZ_RESULT"
        const val EXTRA_DOC_NUM = "EXTRA_DOC_NUM"
        const val EXTRA_DOB = "EXTRA_DOB"
        const val EXTRA_DOE = "EXTRA_DOE"
    }

    private lateinit var binding: ActivityNfcReadBinding
    private var nfcAdapter: NfcAdapter? = null
    private val reader = NfcPassportReader()
    private var mrzResult: MrzResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNfcReadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mrzResult = intent.getParcelableExtra(EXTRA_MRZ_RESULT)
        if (mrzResult == null) {
            val docNum = intent.getStringExtra(EXTRA_DOC_NUM)
            val dob = intent.getStringExtra(EXTRA_DOB)
            val doe = intent.getStringExtra(EXTRA_DOE)
            if (docNum != null && dob != null && doe != null) {
                val line2 = "$docNum<...$dob...$doe..."
                mrzResult = MrzResult(line1 = "", line2 = line2, line3 = null, format = com.example.emrtdreader.models.MrzFormat.TD3)
            }
        }

        if (mrzResult == null) {
            Toast.makeText(this, "No valid MRZ data found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.readNfcButton.setOnClickListener { enableReaderMode() }
    }

    private fun enableReaderMode() {
        binding.readNfcButton.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        nfcAdapter?.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B, null)
    }

    override fun onTagDiscovered(tag: Tag) {
        lifecycleScope.launch {
            runCatching { 
                withContext(Dispatchers.IO) { reader.read(tag, mrzResult!!) }
            }.onSuccess { passportReadResult ->
                openResult(passportReadResult.passportData)
            }.onFailure { error ->
                runOnUiThread {
                    Toast.makeText(this@NFCReadActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun openResult(passportData: PassportData) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("PASSPORT_DATA", passportData)
        }
        startActivity(intent)
        finish()
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onResume() {
        super.onResume()
        if (binding.progressBar.visibility == View.VISIBLE) {
             nfcAdapter?.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B, null)
        }
    }
}