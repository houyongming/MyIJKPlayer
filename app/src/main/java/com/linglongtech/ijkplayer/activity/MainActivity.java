package com.linglongtech.ijkplayer.activity;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.linglongtech.ijkplayer.R;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private Button playBtn1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        playBtn1 = (Button)this.findViewById(R.id.play1);
        playBtn1.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.play1:
                Intent mIntent = new Intent(MainActivity.this,PlayerActivity.class);
                this.startActivity(mIntent);
                break;
            default:
                break;
        }
    }
}
