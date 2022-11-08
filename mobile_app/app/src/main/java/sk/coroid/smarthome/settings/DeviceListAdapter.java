package sk.coroid.smarthome.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.arch.core.util.Function;

import java.util.List;
import java.util.function.BiFunction;

public class DeviceListAdapter extends ReadOnlyDeviceListAdapter {

    private BiFunction onItemCheckedFunction;
    private Function onItemDeleteFunction;

    public DeviceListAdapter(Context context, List<Device> devices, BiFunction<Boolean, Device, Void> onItemCheckedFunction, final Function<Device, Void> onDeleteFunction) {
        super(context, devices);
        this.onItemCheckedFunction = onItemCheckedFunction;
        this.onItemDeleteFunction = onDeleteFunction;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return new DeviceListItem(context, devices.get(position), onItemCheckedFunction, onItemDeleteFunction);
    }

}
