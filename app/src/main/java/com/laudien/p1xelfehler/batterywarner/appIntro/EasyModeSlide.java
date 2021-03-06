package com.laudien.p1xelfehler.batterywarner.appIntro;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import com.laudien.p1xelfehler.batterywarner.R;

import agency.tango.materialintroscreen.SlideFragment;

public class EasyModeSlide extends SlideFragment {
    public EaseModeSlideDelegate delegate;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.slide_easy_mode, container, false);
        RadioGroup radioGroup = view.findViewById(R.id.radio_group);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (delegate != null) {
                    boolean easyMode = i == R.id.btn_easy;
                    delegate.onModeSelected(easyMode);
                }
            }
        });
        view.findViewById(R.id.btn_faq).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Uri uri = Uri.parse(getString(R.string.link_faq));
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                if (intent.resolveActivity(view.getContext().getPackageManager()) != null) {
                    startActivity(intent);
                }
            }
        });
        return view;
    }

    @Override
    public int backgroundColor() {
        return R.color.colorIntro4;
    }

    @Override
    public int buttonsColor() {
        return R.color.colorButtons;
    }

    @Override
    public boolean canMoveFurther() {
        return true;
    }

    @Override
    public String cantMoveFurtherErrorMessage() {
        return null;
    }

    public interface EaseModeSlideDelegate {
        void onModeSelected(boolean easyMode);
    }
}
