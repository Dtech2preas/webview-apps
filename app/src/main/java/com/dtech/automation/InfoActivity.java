package com.dtech.automation;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.Button;
import android.widget.TextView;

public class InfoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        TextView tvInfo = findViewById(R.id.tv_info_text);
        tvInfo.setMovementMethod(LinkMovementMethod.getInstance());

        String infoText = "<b>IMPORTANT NOTE:</b><br/>" +
                "<font color='#FF0000'>The app must be left ACTIVE and on the screen at all times. This is the only way to ensure accurate results.</font><br/><br/>" +
                "<b>BEST PRACTICES:</b><br/>" +
                "It is best to run this process at night while you sleep.<br/>" +
                "1. Connect your charger.<br/>" +
                "2. Enable <b>Developer Options</b> on your phone.<br/>" +
                "3. Enable the <b>'Stay Awake'</b> setting (Screen will never sleep while charging).<br/><br/>" +
                "<b>HOW TO USE:</b><br/><br/>" +
                "1. Open App and go to <b>Settings</b>.<br/>" +
                "2. Input your <b>email:password</b> pairs (one per line).<br/>" +
                "3. If your list is not in the correct format, you can upload your text file to our extractor website:<br/>" +
                "<a href='http://www.preasx24.co.za/ext.html'>www.preasx24.co.za/ext.html</a><br/>" +
                "(It extracts email:pass pairs for you to copy and paste back here).<br/>" +
                "4. <b>Save</b> your settings.<br/>" +
                "5. Press <b>PLAY</b> on the main screen.<br/><br/>" +
                "The app will test all accounts one by one and log results.<br/><br/>" +
                "<b>AD SYSTEM:</b><br/><br/>" +
                "This app is supported by ads. An auto-ad will load every <b>30 minutes</b>. This may disrupt the automation flow.<br/><br/>" +
                "<b>To avoid disruption:</b><br/>" +
                "You can manually watch ads in the 'Ad System' menu to build up buffer time.<br/>" +
                "- <b>+50 mins</b> per ad initially.<br/>" +
                "- <b>+30 mins</b> if buffer > 14 hours.<br/>" +
                "- <b>+15 mins</b> if buffer > 28 hours.<br/><br/>" +
                "Go to the Ad System screen to check your timer and extend it!";

        tvInfo.setText(Html.fromHtml(infoText, Html.FROM_HTML_MODE_LEGACY));
    }
}
