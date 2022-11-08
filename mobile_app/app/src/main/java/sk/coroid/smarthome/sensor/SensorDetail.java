package sk.coroid.smarthome.sensor;

import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.DefaultAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import sk.coroid.smarthome.R;
import sk.coroid.smarthome.SmartHomeClient;
import sk.coroid.smarthome.dto.DHTDataDTO;

public class SensorDetail extends AppCompatActivity {

    private String sensorName = "";
    private SmartHomeClient smartHomeClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_detail);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        smartHomeClient = new SmartHomeClient(this);
        sensorName = getIntent().getStringExtra("EXTRA_SENSOR_NAME");
        LineChart chart = (LineChart) findViewById(R.id.tempchart);
//        smartHomeClient.getDHTData(dhtDataDTOS -> {
//            List<Entry> tempData = new ArrayList<>();
//            List<Entry> humData = new ArrayList<>();
//            long min = OffsetDateTime.MAX.toEpochSecond();
//            long max = 0;
//            for (List<DHTDataDTO> dhtDataDTO : dhtDataDTOS) {
//                for (DHTDataDTO dataDTO : dhtDataDTO) {
//                    long epoch = dataDTO.getDate().toEpochSecond();
//                    if(epoch<min){
//                        min = epoch;
//                    }
//                    if(epoch>max){
//                        max = epoch;
//                    }
//                }
//            }
//            //temp
//            for (List<DHTDataDTO> dhtDataDTO : dhtDataDTOS) {
//                for (DHTDataDTO dataDTO : dhtDataDTO) {
//                    long epoch = dataDTO.getDate().toEpochSecond();
//                    tempData.add(0,new Entry(epoch-min, dataDTO.getTemp().floatValue()));
//                    humData.add(0,new Entry(epoch-min, dataDTO.getHum().floatValue()));
//                }
//            }
//            List<ILineDataSet> dataSets = new ArrayList<>();
//            LineDataSet tempDataSet = new LineDataSet(tempData, "Teplota");
//            tempDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
//            tempDataSet.setColor(getColor(R.color.colorTempLine));
//            tempDataSet.setCircleColor(getColor(R.color.colorTempCircle));
//            dataSets.add(tempDataSet);
//            LineDataSet humDataSet = new LineDataSet(humData, "Vlhkosť");
//            humDataSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
//            humDataSet.setColor(getColor(R.color.colorHumLine));
//            humDataSet.setCircleColor(getColor(R.color.colorHumCircle));
//            dataSets.add(humDataSet);
//            chart.setData(new LineData(dataSets));
//            chart.getAxisLeft().setTextColor(getColor(R.color.colorTextBright));
//            chart.getAxisRight().setTextColor(getColor(R.color.colorTextBright));
//            chart.getLineData().setValueTextColor(getColor(R.color.colorTextBright));
//            chart.getLegend().setTextColor(getColor(R.color.colorTextBright));
//            chart.getDescription().setEnabled(false);
//            XAxis xAxis = chart.getXAxis();
//            xAxis.setGranularity(300f);
//            xAxis.setTextColor(getColor(R.color.colorTextBright));
//            long finalMin = min;
//            xAxis.setValueFormatter(new ValueFormatter() {
//                @Override
//                public String getAxisLabel(float value, AxisBase axis) {
//                    return OffsetDateTime.ofInstant(Instant.ofEpochSecond((long)value+ finalMin), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"));
//                }
//            });
//            chart.invalidate();
//            //humidity
//            for (List<DHTDataDTO> dhtDataDTO : dhtDataDTOS) {
//                for (DHTDataDTO dataDTO : dhtDataDTO) {
//                    long epoch = dataDTO.getDate().toEpochSecond();
//                }
//            }
//            return null;
//        },200, 0, new String[]{sensorName});
    }



    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}