package sk.coroid.smarthome.settings;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
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

public class DeviceListItem extends RelativeLayout {

    private TextView mText1;
    private TextView mText2;

    public DeviceListItem(final Context context, final Device device, final BiFunction<Boolean, Device, Void> onCheckedFunction, final Function<Device, Void> onDeleteFunction) {
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
//        mText1.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        mText2 = new TextView(this.getContext());
        mText2.setText(device.getDeviceBTAddress());
//        mText1.setTextSize(20);
//        mText2.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        horizontal.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        vertical.addView(mText1);
        vertical.addView(mText2);
        horizontal.addView(vertical);
        LinearLayout middleVertical = new LinearLayout(context);
        middleVertical.setOrientation(LinearLayout.VERTICAL);
        Switch enabled = new Switch(context);
        enabled.setChecked(device.isEnabled());
        enabled.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onCheckedFunction.apply(isChecked, device);
            }
        });
        middleVertical.setPadding(0, 0, 300, 0);
        middleVertical.addView(enabled);
        middleVertical.setGravity(Gravity.CENTER_HORIZONTAL);
        middleVertical.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));


        horizontal.addView(middleVertical);

        LinearLayout relativeRight = new LinearLayout(context);
        Button delete = new Button(context);
        delete.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(context)
                        .setTitle("Zmazať zariadenie?")
                        .setMessage("Zariadenie nebude možné ďalej používať pre SmartHome")
                        .setIcon(android.R.drawable.ic_delete)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                onDeleteFunction.apply(device);
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                            }
                        }).show();
            }
        });

        Drawable trashbin = context.getResources().getDrawable(R.drawable.trash, null);
        trashbin.setBounds(0, 0, 130, 130);
        delete.setCompoundDrawables(null, trashbin, null, null);
        LayoutParams layoutParams = new LayoutParams(200, 200);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        relativeRight.setLayoutParams(layoutParams);
        relativeRight.addView(delete);

        root.addView(horizontal);
        this.addView(root);
        this.addView(relativeRight);
    }

}
