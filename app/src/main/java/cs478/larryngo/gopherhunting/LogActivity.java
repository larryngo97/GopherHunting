package cs478.larryngo.gopherhunting;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class LogActivity extends AppCompatActivity{
    protected TextView tv_log;


    public void updateLog() {
        ((TextView)findViewById(R.id.tv_log_text)).setText(MainActivity.text_log);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        tv_log = findViewById(R.id.tv_log_text);
        tv_log.setText(MainActivity.text_log);
    }


}
