#include <Timezone.h>
#include <time.h>

TimeChangeRule CET = {"CET", Last, Sun, Oct, 3, 60};   // Central European Standard Time
TimeChangeRule CEST = {"CEST", Last, Sun, Mar, 2, 120}; // Central European Summer Time
Timezone european(CET, CEST);

time_t getDateTime(time_t t){
	return european.toLocal(t);
}
