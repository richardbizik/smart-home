package sk.coroid.smarthome;

public interface Constants {
    int MAX_COMMAND_RETRY = 5;
    int COMMAND_WAIT = 500; //ms

    int NOTIFICATION_STATE_TRANSFER_DELAY = 30000; //ms
    String NOTIFICATION_RUNNING_MESSAGE = "SmartHome is running";

    float MAX_ALLOWED_DISTANCE = 200f; //distance from which confirmation popup shows
    float BASE_OPEN_DISTANCE_SMALL = 60f; //open gate
    float BASE_OPEN_DISTANCE_MEDIUM = 600f;
    float BASE_OPEN_DISTANCE_LARGE = 4500f;
    long BASE_OPEN_DISTANCE_INTERVAL_SMALL = 1000; //check every 1s
    long BASE_OPEN_DISTANCE_INTERVAL_MEDIUM = 30 * 1000; //check every 30s
    long BASE_OPEN_DISTANCE_INTERVAL_LARGE = 2 * 60 * 1000; //check every 2m
}
