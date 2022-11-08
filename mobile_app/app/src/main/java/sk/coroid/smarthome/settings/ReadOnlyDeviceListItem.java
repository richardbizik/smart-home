package sk.coroid.smarthome.settings;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.arch.core.util.Function;

import java.util.function.BiFunction;

import sk.coroid.smarthome.R;

public class ReadOnlyDeviceListItem extends RelativeLayout {

    private TextView mText1;
    private TextView mText2;

    public ReadOnlyDeviceListItem(final Context context, final Device device) {
        super(context);
        LinearLayout root = new LinearLayout(context);
        root.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout horizontal = new LinearLayout(context);
        LinearLayout vertical = new LinearLayout(context);
        vertical.setOrientation(LinearLayout.VERTICAL);
        mText1 = new TextView(this.getContext());
        mText1.setTextSize(30);
        mText1.setText(device.getDeviceName());
        mText2 = new TextView(this.getContext());
        mText2.setText(device.getDeviceBTAddress());
        horizontal.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        vertical.addView(mText1);
        vertical.addView(mText2);
        horizontal.addView(vertical);
        root.addView(horizontal);
        this.addView(root);
    }

}
