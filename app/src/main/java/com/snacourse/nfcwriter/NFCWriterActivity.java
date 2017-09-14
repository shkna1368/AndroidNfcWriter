package com.snacourse.nfcwriter;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;

public class NFCWriterActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "SmartBookWriter";

    // NFC-related variables
    NfcAdapter mNfcAdapter;
    PendingIntent mNfcPendingIntent;
    IntentFilter[] mReadTagFilters;
    IntentFilter[] mWriteTagFilters;
    AlertDialog mWriteTagDialog;

    private boolean mWriteMode = false;
    EditText editCharge;
    EditText editDate;
    Button btnSave;
    AlertDialog.Builder builder;
    AlertDialog.Builder alertDialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfcwriter);

        editCharge=findViewById(R.id.txtCharge);
        editDate=findViewById(R.id.txtDate);
        btnSave=findViewById(R.id.buttonSave);
btnSave.setOnClickListener(this);

        editDate.setText(new Date().toString());
        checkNFCSupport();
        initNFCListener();
    }

    private  void checkNFCSupport(){
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (mNfcAdapter == null)
        {
            Toast.makeText(this,
                    "Your device does not support NFC. Cannot run demo.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }

    @Override
    protected void onResume()
    {  super.onResume();

        // Double check if NFC is enabled
        checkNfcEnabled();

        Log.d(TAG, "onResume: " + getIntent());

        mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent,
                mReadTagFilters, null);
    }

    /* Called when the system is about to start resuming a previous activity. */
    @Override
    protected void onPause()
    {
        super.onPause();
        Log.d(TAG, "onPause: " + getIntent());

        mNfcAdapter.disableForegroundDispatch(this);
    }


    private void initNFCListener(){

        mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        // Create intent filter to handle NDEF NFC tags detected from inside our
        // application when in "read mode":
        IntentFilter ndefDetected = new IntentFilter(
                NfcAdapter.ACTION_NDEF_DISCOVERED);
        try
        {
            ndefDetected.addDataType("application/root.gast.playground.nfc");
        } catch (IntentFilter.MalformedMimeTypeException e)
        {
            throw new RuntimeException("Could not add MIME type.", e);
        }

        // Create intent filter to detect any NFC tag when attempting to write
        // to a tag in "write mode"
        IntentFilter tagDetected = new IntentFilter(
                NfcAdapter.ACTION_TAG_DISCOVERED);

        // create IntentFilter arrays:
        mWriteTagFilters = new IntentFilter[] { tagDetected };
        // mReadTagFilters = new IntentFilter[] { ndefDetected, tagDetected };
    }




    private NdefMessage createNdefFromJson()
    {

        // get the values from the form's text fields:
        String chrage  = editCharge.getText().toString();
        String date  = editDate.getText().toString();


        // create a JSON object out of the values:
        JSONObject jsonObjectData = new JSONObject();
        try
        {
            jsonObjectData.put("charge", chrage);
            jsonObjectData.put("date", date);


        } catch (JSONException e)
        {
            Log.e(TAG, "Could not create JSON: ", e);
        }

        // create a new NDEF record and containing NDEF message using the app's
        // custom MIME type:
        String mimeType = "application/root.gast.playground.nfc";
        byte[] mimeBytes = mimeType.getBytes(Charset.forName("UTF-8"));
        String data = jsonObjectData.toString();
        byte[] dataBytes = data.getBytes(Charset.forName("UTF-8"));
        byte[] id = new byte[0];
        NdefRecord record = new NdefRecord(NdefRecord.TNF_MIME_MEDIA,
                mimeBytes, id, dataBytes);
        NdefMessage m = new NdefMessage(new NdefRecord[] { record });

        // return the NDEF message
        return m;
    }

    private void enableTagWriteMode()
    {
        mWriteMode = true;
        mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent,
                mWriteTagFilters, null);
    }


    @Override
    protected void onNewIntent(Intent intent)
    {
        Log.d(TAG, "onNewIntent: " + intent);

        // Currently in tag WRITING mode
        if (intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED))
        {
            Tag detectedTag = intent
                    .getParcelableExtra(NfcAdapter.EXTRA_TAG);
            writeTag(createNdefFromJson(), detectedTag);
            mWriteTagDialog.cancel();
        }
    }




    boolean writeTag(NdefMessage message, Tag tag)
    {
        int size = message.toByteArray().length;

        try
        {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null)
            {
                ndef.connect();

                if (!ndef.isWritable())
                {
                    Toast.makeText(this,
                            "Cannot write to this tag. This tag is read-only.",
                            Toast.LENGTH_LONG).show();
                    return false;
                }
                if (ndef.getMaxSize() < size)
                {
                    Toast.makeText(
                            this,
                            "Cannot write to this tag. Message size (" + size
                                    + " bytes) exceeds this tag's capacity of "
                                    + ndef.getMaxSize() + " bytes.",
                            Toast.LENGTH_LONG).show();
                    return false;
                }

                ndef.writeNdefMessage(message);
                Toast.makeText(this,
                        "A pre-formatted tag was successfully updated.",
                        Toast.LENGTH_LONG).show();
                editCharge.setText("");
                editDate.setText("");

                return true;
            } else
            {
                NdefFormatable format = NdefFormatable.get(tag);
                if (format != null)
                {
                    try
                    {
                        format.connect();
                        format.format(message);
                        Toast.makeText(
                                this,
                                "This tag was successfully formatted and updated.",
                                Toast.LENGTH_LONG).show();
                        return true;
                    } catch (IOException e)
                    {
                        Toast.makeText(
                                this,
                                "Cannot write to this tag due to I/O Exception.",
                                Toast.LENGTH_LONG).show();
                        return false;
                    }
                } else
                {
                    Toast.makeText(
                            this,
                            "Cannot write to this tag. This tag does not support NDEF.",
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        } catch (Exception e)
        {
            Toast.makeText(this,
                    "Cannot write to this tag due to an Exception.",
                    Toast.LENGTH_LONG).show();
        }

        return false;
    }

    /*
     * **** HELPER METHODS ****
     */

    private void checkNfcEnabled()
    {
        Boolean nfcEnabled = mNfcAdapter.isEnabled();
        if (!nfcEnabled)
        {
            alertDialog=     new AlertDialog.Builder(this)  ;
            alertDialog  .setTitle("NFC is off")  ;
            alertDialog.setMessage("Turn on nfc") ;
            alertDialog.setCancelable(false);
            alertDialog.setPositiveButton("Update Settings",
                    new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog,
                                            int id)
                        {
                            startActivity(new Intent(
                                    android.provider.Settings.ACTION_WIRELESS_SETTINGS));
                        }
                    }).create().show();
            alertDialog.create().cancel();
            alertDialog.create().dismiss();
        }
    }

    @Override
    public void onClick(View view) {
        enableTagWriteMode();

        builder = new AlertDialog.Builder(
               this)
                .setTitle("Ready to write")
                .setMessage("ready to write")
                .setCancelable(true)
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog,
                                                int id)
                            {
                                dialog.cancel();
                            }
                        })
                .setOnCancelListener(new DialogInterface.OnCancelListener()
                {
                    @Override
                    public void onCancel(DialogInterface dialog)
                    {
                        builder.create().dismiss();
                    }
                });
        mWriteTagDialog = builder.create();
        mWriteTagDialog.show();
    }
    }

