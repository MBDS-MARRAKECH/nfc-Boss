package org.mbds.nfctag.write;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import org.mbds.nfctag.R;
import org.mbds.nfctag.model.TagType;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public class NFCWriterActivity extends AppCompatActivity {
    public static final  String Error_Detected="No NFC Tag Detected";
    public static final String Write_Success="Text Written Successfully!";
    public static final String Write_Error="Error during Writing, Try Again!";
    IntentFilter writingTagFilters[];
    boolean writeMode;
    Tag myTag;
    Context context;
    TextView edit_message;
    TextView nfc_contents;
    Button ActivateButton;

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;

    private NfcTagViewModel viewModel;

    // TODO Analyser le code et comprendre ce qui est fait
    // TODO Ajouter un formulaire permettant à un utilisateur d'entrer le texte à mettre dans le tag
    // TODO Le texte peut être 1) une URL 2) un numéro de téléphone 3) un plain texte
    // TODO Utiliser le view binding
    // TODO L'app ne doit pas crasher si les tags sont mal formattés

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.write_tag_layout);

        edit_message=(TextView) findViewById(R.id.edit_message);
        nfc_contents=(TextView)findViewById(R.id.nfc_contents);
        ActivateButton=findViewById(R.id.ActivateButton);
        context=this;
        ActivateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    if(myTag==null){
                        Toast.makeText(context, Error_Detected,Toast.LENGTH_SHORT).show();
                    }else{
                        write("PlainText |"+edit_message.getText().toString(), myTag);
                        Toast.makeText(context, Write_Success, Toast.LENGTH_SHORT).show();
                    }
                }catch (IOException e){
                    Toast.makeText(context, Write_Success, Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }catch (FormatException e){
                    Toast.makeText(context,Write_Error, Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }
        });
        nfcAdapter=NfcAdapter.getDefaultAdapter(this);
        if(nfcAdapter==null){
            Toast.makeText(this,"ce device ne supporte pas le NFC",Toast.LENGTH_SHORT).show();
            finish();
        }
        readfromIntent(getIntent());
        pendingIntent=PendingIntent.getActivity(this,0,new Intent(this,getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),0);
        IntentFilter tagDetected=new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
        writingTagFilters=new IntentFilter[]{tagDetected};


        // init ViewModel
        viewModel = new ViewModelProvider(this).get(NfcTagViewModel.class);


        //Get default NfcAdapter and PendingIntent instances
       // nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        // check NFC feature:
        //if (nfcAdapter == null) {
            // process error device not NFC-capable…

        //}
        //pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).
              //  addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        // single top flag avoids activity multiple instances launching
    }

    private void readfromIntent(Intent intent){
        String action=intent.getAction();
        if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
        || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
        || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)){
            Parcelable[] rawMsgs=intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs=null;
            if(rawMsgs != null){
                msgs=new NdefMessage[rawMsgs.length];
                for(int i=0;i<rawMsgs.length;i++){
                    msgs[i]=(NdefMessage) rawMsgs[i];
                }
            }
            buildTagViews(msgs);
        }
    }

    private void buildTagViews(NdefMessage[] msgs){
        if(msgs == null || msgs.length==0)return;
        String text="";
        //String tagId=new String(msgs[0].getRecords()[0].getType());
        byte[] payload=msgs[0].getRecords()[0].getPayload();
        String textEncoding=((payload[0] & 128)==0)? "UTF-8":"UTF-16";
        int languageCodeLength=payload[0] & 0063;
        //String languageCode=new String(payload,1,languageCodeLength,"US-ASCII");

        try {
            //get the text
            text=new String(payload,languageCodeLength+1,payload.length-languageCodeLength-1,textEncoding);
        }catch (UnsupportedEncodingException e){
            Log.e("UnsupportedEncoding",e.toString());
        }
        nfc_contents.setText("NFC Content :"+text);
    }

    private void write(String text, Tag tag) throws IOException,FormatException{
        NdefRecord[] records={createRecord(text)};
        NdefMessage message=new NdefMessage(records);
        //get an instance of Ndef for the tag
        Ndef ndef=Ndef.get(tag);
        //Enable I/O
        ndef.connect();
        //Write the message
        ndef.writeNdefMessage(message);
        //Close the connection
        ndef.close();
    }

    private NdefRecord createRecord(String text) throws UnsupportedEncodingException{
        String lang="en";
        byte[] textBytes=text.getBytes();
        byte[] langBytes=lang.getBytes("US-ASCII");
        int langLength=langBytes.length;
        int textLength=textBytes.length;
        byte[] payload=new byte[ +langLength+textLength];

        //set status byte (see NDEF spec for actual bits)
        payload[0]=(byte) langLength;

        //copy langbytes and textbytes into payload
        System.arraycopy(langBytes,0,payload,1,langLength);
        System.arraycopy(textBytes,0,payload,1+langLength,textLength);
        NdefRecord recordNDF=new NdefRecord(NdefRecord.TNF_WELL_KNOWN,NdefRecord.RTD_TEXT,new byte[0],payload);
        return recordNDF;
    }





    /**
     * This method is called when a new intent is detected by the system, for instance when a new NFC tag is detected.
     *
     * @param intent The new intent that was started for the activity.
     */
    @Override
    public void onNewIntent(Intent intent) {

        super.onNewIntent(intent);
        setIntent(intent);
        readfromIntent(intent);
        if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())){
            myTag=intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        }
        /*String action = intent.getAction();
        // check the event was triggered by the tag discovery
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            // get the tag object from the received intent
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            viewModel.writeTag("hello MBDS", tag, TagType.TEXT);
        } else {
            // TODO Indiquer à l'utilisateur que ce type de tag n'est pas supporté
        }*/
    }

    @Override
    public void onPause() {
        super.onPause();

        //Disable NFC foreground detection
        WriteModeOff();
    }
    @Override
    public void onResume() {
        super.onResume();
        WriteModeOn();
    }

    private void WriteModeOn(){
        writeMode=true;
        nfcAdapter.enableForegroundDispatch(this,pendingIntent,writingTagFilters,null);
    }

    private void WriteModeOff(){
        writeMode=false;
        nfcAdapter.disableForegroundDispatch(this);
    }

}
