package sk.coroid.smarthome.sensor;

import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.time.format.DateTimeFormatter;
import java.util.List;

import sk.coroid.smarthome.R;
import sk.coroid.smarthome.dto.DHTDataDTO;
import sk.coroid.smarthome.settings.SettingsActivity;

public class SensorCardAdapter extends RecyclerView.Adapter<SensorCardAdapter.viewHolder> {
    Context context;
    List<DHTDataDTO> list;

    public SensorCardAdapter(Context context, List<DHTDataDTO> arrayList) {
        this.context = context;
        this.list = arrayList;
    }

    @Override
    public  SensorCardAdapter.viewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(context).inflate(R.layout.sensor_card, viewGroup, false);
        return new viewHolder(view);
    }
    @Override
    public  void onBindViewHolder(SensorCardAdapter.viewHolder holder, int position) {
        holder.itemView.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, SensorDetail.class);
                intent.putExtra("EXTRA_SENSOR_NAME", list.get(position).getSensorName());
                context.startActivity(intent);
            }
        });
        holder.temp.setText(String.format("%.1f°C", list.get(position).getTemp()));
        holder.batt.setText(String.format("%.2fV", list.get(position).getBatt()));
        holder.hum.setText(String.format("%.1f%%", list.get(position).getHum()));
        holder.place.setText(list.get(position).getSensorName());
        holder.date.setText(list.get(position).getDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
        if(list.get(position).getBatt()<2.8f) {
            holder.cardView.setBackgroundColor(context.getResources().getColor(R.color.redAccent));
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public class viewHolder extends RecyclerView.ViewHolder {
        TextView place, temp, hum, batt, date;
        CardView cardView;

        public viewHolder(View itemView) {
            super(itemView);
            place = (TextView) itemView.findViewById(R.id.place);
            temp = (TextView) itemView.findViewById(R.id.temp);
            hum = (TextView) itemView.findViewById(R.id.hum);
            batt = (TextView) itemView.findViewById(R.id.batt);
            date = (TextView) itemView.findViewById(R.id.date);
            cardView = itemView.findViewById(R.id.card_view);
        }
    }
}
